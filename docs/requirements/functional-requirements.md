# Functional Requirements — device-details-service (MVNO-001)

**Ticket:** MVNO-001
**Stage:** 1 — Requirements
**Version:** 1.0.0
**Status:** Draft

---

## FR-01: Device Query by MSISDN and MVNO Name

The service SHALL expose a synchronous REST API endpoint that accepts an MSISDN (E.164 format) and an MVNO name as inputs and returns the associated device details for that subscriber.

- The MSISDN MUST be a valid E.164 formatted phone number (e.g., `+14155551234`).
- The MVNO name MUST be a non-empty string matching a registered MVNO identifier in the system.
- Both parameters are REQUIRED. Requests missing either parameter MUST be rejected with HTTP 400.
- The endpoint MUST be idempotent — repeated identical requests MUST return consistent results given no state change in the vendor backend.

---

## FR-02: Tenant Isolation — MVNO-Scoped Data Access

The service SHALL enforce strict tenant isolation such that an authenticated MVNO partner can only retrieve device records belonging to subscribers of their own MVNO.

- The MVNO identity resolved from the authentication credential (API key or OAuth2 token) MUST be compared against the `mvno` parameter provided in the request.
- If the authenticated MVNO identity does NOT match the requested MVNO name, the service MUST reject the request with HTTP 403 (Forbidden).
- Cross-MVNO data access MUST NOT be permitted under any condition, including administrative contexts unless explicitly scoped.
- The tenant binding MUST be enforced at the service layer before any downstream vendor call is initiated.

---

## FR-03: Integration with Third-Party Vendor Backend

The service SHALL act as a middleware proxy that retrieves device information from a third-party device vendor API using the MSISDN as the lookup key.

- The service MUST translate the inbound request into the vendor API's required request format.
- The service MUST authenticate with the vendor backend using credentials configured externally (not hardcoded).
- The vendor API call MUST be made after successful authentication and tenant isolation checks.
- The service MUST support configurable vendor base URL, timeout, and retry parameters via externalized configuration.
- The vendor integration MUST NOT expose vendor-internal identifiers, error messages, or proprietary response structures directly to the MVNO partner.

---

## FR-04: Response Mapping — Vendor Fields to Canonical API Response

The service SHALL transform the vendor backend response into a canonical response model before returning it to the MVNO partner.

The canonical response MUST include the following fields when available from the vendor:

| Field         | Type    | Description                                     |
|---------------|---------|-------------------------------------------------|
| `msisdn`      | String  | The E.164 MSISDN used in the query              |
| `mvno`        | String  | The MVNO name associated with the subscriber    |
| `deviceName`  | String  | Human-readable device name                     |
| `make`        | String  | Device manufacturer name                        |
| `model`       | String  | Device model identifier                         |
| `hasVolte`    | Boolean | VoLTE support flag                              |
| `imei`        | String  | International Mobile Equipment Identity         |
| `imsi`        | String  | International Mobile Subscriber Identity        |

- Additional vendor fields available in the response MUST be included in an `additionalAttributes` map of type `Map<String, Object>`.
- Fields not returned by the vendor MUST be represented as `null` in the canonical response; they MUST NOT be omitted.
- The mapping logic MUST be encapsulated and independently configurable to accommodate future vendor field changes without modifying core service logic.

---

## FR-05: Error Handling — Subscriber Not Found

When the vendor backend returns a response indicating the MSISDN does not exist or belongs to no known device, the service SHALL:

- Return HTTP 404 (Not Found) to the MVNO partner.
- Include a structured error response body with an `errorCode` of `SUBSCRIBER_NOT_FOUND` and a human-readable `message`.
- NOT expose vendor-specific error codes or messages in the response.
- Log the occurrence with the MSISDN (masked for PII compliance), MVNO name, and correlation ID.

---

## FR-06: Error Handling — Vendor Unavailable

When the third-party vendor backend is unreachable, returns a 5xx response, or exceeds the configured timeout, the service SHALL:

- Apply the configured resilience strategy (retry with backoff and/or circuit breaker) before concluding the vendor is unavailable.
- Return HTTP 503 (Service Unavailable) to the MVNO partner after resilience mechanisms are exhausted.
- Include a structured error response body with an `errorCode` of `VENDOR_UNAVAILABLE` and a human-readable `message`.
- NOT propagate vendor-internal HTTP status codes directly to the caller.
- Record the failure event in application logs and increment the `vendor.backend.error` metric counter.

---

## FR-07: Error Handling — Unauthorized MVNO Partner

When a request is received from a caller that cannot be authenticated or whose identity does not correspond to any registered MVNO, the service SHALL:

- Return HTTP 401 (Unauthorized) if no valid credentials are present.
- Return HTTP 403 (Forbidden) if credentials are valid but the MVNO identity does not match the requested MVNO or lacks permission to access the endpoint.
- Include a structured error response body with the appropriate `errorCode`: `AUTHENTICATION_FAILED` or `ACCESS_DENIED`.
- NOT return any device information in error responses.
- Log the unauthorized attempt with the caller IP, credential identifier (masked), and requested MVNO.

---

## FR-08: Error Handling — Input Validation

The service SHALL validate all inbound request parameters before processing.

- MSISDN MUST conform to E.164 format. Invalid values MUST result in HTTP 400 with `errorCode` `INVALID_MSISDN`.
- MVNO name MUST be a non-empty alphanumeric string (hyphens and underscores permitted). Invalid values MUST result in HTTP 400 with `errorCode` `INVALID_MVNO_NAME`.
- All validation error responses MUST include a `violations` array listing the field name, rejected value (masked for PII), and reason.

---

## FR-09: Audit Logging of All Queries

The service SHALL record an audit log entry for every inbound request, regardless of outcome.

Each audit log entry MUST capture:

| Field              | Description                                              |
|--------------------|----------------------------------------------------------|
| `correlationId`    | Unique request trace identifier (UUID)                   |
| `timestamp`        | UTC timestamp of request receipt (ISO 8601)              |
| `mvno`             | MVNO name from the request                               |
| `msisdn`           | Masked MSISDN (last 4 digits visible, remainder as `*`)  |
| `callerIp`         | Source IP address of the requesting client               |
| `credentialId`     | Identifier of the API key or OAuth2 client used          |
| `httpMethod`       | HTTP method of the request                               |
| `endpoint`         | Request URI path                                         |
| `responseStatus`   | HTTP status code returned                                |
| `durationMs`       | Total request processing time in milliseconds            |
| `outcome`          | One of: `SUCCESS`, `NOT_FOUND`, `VENDOR_ERROR`, `AUTH_FAILURE`, `VALIDATION_ERROR` |

- Audit logs MUST be written to a dedicated, structured (JSON) log stream separate from application logs.
- Audit log entries MUST NOT be dropped due to application errors — write failures to the audit stream MUST be logged to the application error stream.
- Audit logs MUST be retained per the organization's data retention policy (externally configured).

---

## FR-10: Health and Readiness Endpoints

The service SHALL expose standard health and readiness endpoints suitable for Kubernetes liveness and readiness probes.

- **Liveness endpoint** (`GET /actuator/health/liveness`): Returns `UP` when the application process is running and not in a deadlocked or unrecoverable state.
- **Readiness endpoint** (`GET /actuator/health/readiness`): Returns `UP` only when the service is fully initialized and capable of processing requests, including successful connectivity to required dependencies (e.g., vendor backend configuration loaded, authentication provider reachable).
- **Aggregated health endpoint** (`GET /actuator/health`): Returns aggregated status including individual component health indicators.
- These endpoints MUST NOT require authentication.
- These endpoints MUST NOT expose sensitive configuration values, credentials, or PII in their responses.

---

## FR-11: Security — Authentication Mechanisms

The service SHALL support the following authentication mechanisms for MVNO partner access:

- **API Key Authentication**: Each MVNO partner is issued a unique API key transmitted via the `X-API-Key` HTTP request header. The service MUST validate the API key against a configured credential store before processing any request.
- **OAuth2 Client Credentials**: The service MUST support OAuth2 client credentials flow, validating JWT bearer tokens issued by a configured Authorization Server. The JWT MUST contain a claim mapping the token to a specific MVNO identity.
- Both mechanisms MUST resolve the caller's MVNO identity for use in tenant isolation (FR-02).
- Credentials MUST NOT be logged in plaintext under any circumstances.

---

## FR-12: Resilience — Vendor Backend Integration

The service SHALL implement the following resilience patterns for all calls to the vendor backend:

- **Timeout**: Each vendor API call MUST be subject to a configurable maximum wait time. Requests exceeding this timeout MUST be aborted and treated as a vendor failure.
- **Retry with Exponential Backoff**: On transient vendor failures (network errors, HTTP 5xx responses), the service MUST retry the vendor call a configurable number of times with exponential backoff and jitter before declaring the vendor unavailable.
- **Circuit Breaker**: The service MUST implement a circuit breaker that opens after a configurable threshold of consecutive failures, preventing further calls to the vendor backend during the open state and allowing periodic probe requests to detect recovery.
- All resilience parameters (timeout duration, retry count, backoff configuration, circuit breaker thresholds) MUST be externalized and configurable without code changes.

---

## FR-13: Response Caching (Optional — Configurable)

The service SHALL support optional, configurable response caching for vendor backend responses.

- Caching MUST be disabled by default.
- When enabled, cache entries MUST be scoped per `(mvno, msisdn)` key pair to maintain tenant isolation.
- Cache TTL MUST be externally configurable.
- Cache entries MUST be invalidated upon vendor error to prevent stale data from being served.
- The cache MUST NOT be shared across MVNO tenants.

---

## FR-14: Correlation ID Propagation

The service SHALL generate and propagate a unique correlation ID for every inbound request.

- If the inbound request contains an `X-Correlation-Id` header, the service MUST use that value as the correlation ID.
- If no `X-Correlation-Id` header is present, the service MUST generate a new UUID.
- The correlation ID MUST be included in all outbound vendor API calls (as a vendor-supported tracing header where available).
- The correlation ID MUST be returned to the caller in the response via the `X-Correlation-Id` header.
- The correlation ID MUST appear in all log entries and audit records generated during the request lifecycle.
