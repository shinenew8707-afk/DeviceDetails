# ADR-0002: Dual Authentication Strategy (API Key + OAuth2 Client Credentials)

**Ticket:** MVNO-001
**Date:** 2026-05-11
**Authors:** Designer Agent (Stage 2)

## Status

Accepted

---

## Context

The device-details-service must be consumed by multiple MVNO partners. Each MVNO partner is an independent organization with its own integration capabilities and security posture:

- **Larger, enterprise MVNO partners** have existing IAM infrastructure and prefer OAuth2 Client Credentials flow with JWT bearer tokens for consistency with their internal security standards.
- **Smaller or legacy MVNO partners** cannot easily integrate OAuth2 and require a simpler, static API key mechanism.
- Both categories of partner must be subject to the same tenant isolation guarantee (FR-02): the authenticated identity must resolve to a specific MVNO and be matched against the requested MVNO parameter on every call.
- A single-mechanism approach would exclude one category of partner or force migration before the service can go live.
- FR-11 explicitly requires support for both API Key (via `X-API-Key` header) and OAuth2 Client Credentials (JWT bearer token via `Authorization: Bearer` header).

---

## Decision

**Support both authentication mechanisms in parallel** via Spring Security's filter chain, with the following design:

### Mechanism 1: API Key

- MVNO partners transmit a pre-issued API key in the `X-API-Key` HTTP request header.
- The service maintains a credential store (in-memory at startup, loaded from externalized config / Kubernetes Secret) mapping API key hashes to MVNO identities.
- Incoming API keys are hashed (SHA-256) and compared against stored hashes — plaintext keys are never stored.
- On match, a `MvnoAuthenticationToken` is populated with the resolved `mvnoId` and set in the Spring Security `SecurityContext`.
- On mismatch or missing header: proceed to next authentication mechanism (OAuth2), not immediate 401.

### Mechanism 2: OAuth2 JWT Bearer Token

- MVNO partners obtain a JWT from a configured external Authorization Server using OAuth2 Client Credentials flow (RFC 6749 §4.4).
- The service validates the JWT signature, expiry, issuer, and audience against configured Authorization Server JWKS endpoint.
- A custom claim (configurable name, default: `mvno_id`) within the JWT payload carries the MVNO identity.
- Spring Security Resource Server (`spring-security-oauth2-resource-server`) handles JWT validation.
- On successful validation, the `mvno_id` claim is extracted and set in the `SecurityContext`.

### Authentication Filter Chain Order

```
CorrelationIdFilter
  → ApiKeyAuthenticationFilter (checks X-API-Key)
  → JwtBearerAuthenticationFilter (Spring Security Resource Server)
  → TenantIsolationFilter (reads authenticated mvnoId from SecurityContext)
  → Controller
```

If neither mechanism authenticates the request, Spring Security returns HTTP 401 with `errorCode: AUTHENTICATION_FAILED`.

### MVNO Identity Resolution

Both mechanisms must resolve to a canonical `mvnoId` (string identifier matching the registered MVNO name). The `TenantIsolationFilter` reads this identity from the `SecurityContext` regardless of which mechanism authenticated the request, ensuring uniform enforcement.

### Credential Storage

- **API keys** are stored as SHA-256 hashes in a `ConfigMap` / `Secret` mounted at startup. Hot-reload is supported via Spring Cloud Config or by restarting pods after Secret update.
- **OAuth2 configuration** (issuer URI, audience, MVNO claim name) is fully externalized via `application.yml` environment variables.
- No credentials are logged, traced, or included in any error response.

---

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| API Key only | Excludes enterprise MVNOs requiring OAuth2; limits future IAM integration |
| OAuth2 only | Excludes smaller MVNOs without OAuth2 capability; increases onboarding friction |
| mTLS (mutual TLS) | High operational overhead for certificate lifecycle management; not required by FR-11 |
| HTTP Basic Auth | Deprecated for machine-to-machine; credentials transmitted per-request in plaintext (even over TLS it is weaker than hashed API keys or signed JWTs) |
| Single filter handling both | Violates single responsibility; harder to independently extend or disable one mechanism |

---

## Consequences

**Positive:**
- All MVNO partner categories can onboard without requiring infrastructure changes on their end.
- Both mechanisms are handled in a single, unified security model; tenant isolation logic does not need to know which mechanism was used.
- API key hashing means a credential store compromise does not expose usable keys.
- OAuth2 path supports token rotation and fine-grained scopes natively.
- Spring Security's Resource Server handles JWT validation boilerplate (JWKS caching, clock skew tolerance).

**Negative:**
- Two authentication code paths increase surface area for security bugs; thorough testing of both paths is mandatory.
- API key revocation requires a pod restart (or config reload) rather than being immediate — acceptable for this use case but must be documented in the operations runbook.
- JWT JWKS endpoint must be reachable from the service at startup and periodically; JWKS unavailability will degrade OAuth2 authentication (API key path remains unaffected).
- Dual mechanism means integration testing must cover both auth flows for every functional scenario.

---

## Security Considerations

| Concern | Mitigation |
|---|---|
| API key exposure in logs | `ApiKeyAuthenticationFilter` masks the key in all log statements; only the resolved `mvnoId` is logged |
| API key brute force | SHA-256 with sufficient key entropy (≥ 32 random bytes) makes brute force infeasible; rate limiting at API gateway layer |
| JWT token replay | JWT `exp` claim enforced; short token lifetime recommended (configurable on Authorization Server) |
| JWT algorithm confusion | Resource Server configured with explicit allowed algorithms (RS256 or ES256 only); `alg: none` rejected |
| Credential committed to source control | API key hashes and OAuth2 client secrets are mounted via Kubernetes Secret — never in source code or `application.yml` committed to git |
| MVNO claim tampering | JWT is signature-verified against Authorization Server's JWKS; unsigned or self-signed JWTs rejected |
| Credential rotation downtime | API keys: zero-downtime rotation via dual-key (old + new) overlap window; OAuth2: Authorization Server handles rotation transparently via JWKS |
