# Data Model — Device Details Service (MVNO-001)

**Version:** 1.0.0
**Stage:** 2 — Design Generated

---

## Table of Contents

1. [Persistence Rationale](#persistence-rationale)
2. [In-Memory Domain Structures](#in-memory-domain-structures)
3. [Vendor Request/Response DTOs](#vendor-requestresponse-dtos)
4. [MVNO Credential Model](#mvno-credential-model)
5. [Cache Data Model](#cache-data-model)
6. [PII Classification and Masking Rules](#pii-classification-and-masking-rules)

---

## Persistence Rationale

This service has **no persistent database**. The design is intentionally stateless for the following reasons:

- **Data ownership**: Subscriber and device data is owned by the third-party vendor backend. The service is a proxy/adaptor; it does not store or replicate the authoritative data.
- **Simplicity and scalability**: Stateless instances can be added or removed by the HPA without coordinating state migration, database connection pools, or distributed locking.
- **Zero-downtime deploys**: Rolling deploy replaces pods one at a time; no schema migrations or backward-compatibility constraints.
- **Compliance**: Avoiding persistence of PII fields (MSISDN, IMEI, IMSI) reduces the compliance surface area for data residency and retention policies.
- **MVNO credentials**: Stored as hashed API keys in Kubernetes Secrets, injected as environment variables; loaded into `MvnoCredentialStore` at startup. Not stored in a database.
- **Optional cache**: If enabled, uses an in-process Caffeine cache. This is ephemeral — each pod has its own cache and it is lost on restart. This is acceptable because the data has a short TTL and can be re-fetched from the vendor on cache cold-start.

---

## In-Memory Domain Structures

### `DeviceDetailsRequest`

Represents the validated inbound request from the MVNO client.

| Field | Type | Constraints | Description |
|---|---|---|---|
| `msisdn` | `String` | Required; E.164 pattern `^\+[1-9]\d{6,14}$` | Mobile subscriber number |
| `mvno` | `String` | Required; not blank; max 100 chars | MVNO tenant identifier |

**Validation annotations (Jakarta Bean Validation):**
```
msisdn: @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "INVALID_MSISDN")
mvno:   @NotBlank @Size(max = 100, message = "INVALID_MVNO_NAME")
```

---

### `DeviceDetailsResponse`

Canonical response returned to MVNO clients. All device fields are nullable to accommodate partial vendor data.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `msisdn` | `String` | No | Echo of requested MSISDN |
| `mvno` | `String` | No | Echo of requested MVNO |
| `deviceName` | `String` | Yes | Human-readable device name (e.g., "iPhone 15 Pro") |
| `make` | `String` | Yes | Device manufacturer (e.g., "Apple") |
| `model` | `String` | Yes | Device model identifier (e.g., "A2849") |
| `hasVolte` | `Boolean` | Yes | VoLTE capability flag |
| `imei` | `String` | Yes | International Mobile Equipment Identity (15 digits) |
| `imsi` | `String` | Yes | International Mobile Subscriber Identity (15 digits) |
| `additionalAttributes` | `Map<String, Object>` | Yes | Vendor-specific extended attributes |
| `correlationId` | `String` | No | Request correlation ID from MDC |

**PII fields**: `imei`, `imsi` — see [PII Classification](#pii-classification-and-masking-rules).

---

### `ApiErrorResponse`

Uniform error envelope returned for all 4xx and 5xx responses.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `errorCode` | `String` | No | Machine-readable error code (see enum below) |
| `message` | `String` | No | Human-readable error description |
| `correlationId` | `String` | No | Request correlation ID for tracing |
| `violations` | `List<Violation>` | Yes | Non-null only for 400 validation errors |

**Error Code Enum:**

| Code | HTTP Status | Trigger |
|---|---|---|
| `INVALID_MSISDN` | 400 | msisdn fails E.164 pattern validation |
| `INVALID_MVNO_NAME` | 400 | mvno is blank or exceeds max length |
| `AUTHENTICATION_FAILED` | 401 | Missing, expired, or malformed credentials |
| `ACCESS_DENIED` | 403 | Authenticated MVNO identity != requested MVNO |
| `SUBSCRIBER_NOT_FOUND` | 404 | Vendor returned no record for MSISDN |
| `VENDOR_UNAVAILABLE` | 503 | Vendor call failed after retries; circuit breaker open |
| `INTERNAL_ERROR` | 500 | Unexpected server-side error |

**`Violation` sub-object:**

| Field | Type | Description |
|---|---|---|
| `field` | `String` | Request parameter name (e.g., "msisdn") |
| `rejectedValue` | `String` | Submitted value that failed validation |
| `reason` | `String` | Constraint violation message |

---

### `AuditEntry`

Structured audit log record written for every request (FR-09). Written asynchronously to dedicated log stream.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `correlationId` | `String` | No | Request correlation ID |
| `timestamp` | `Instant` | No | UTC timestamp of request receipt (ISO-8601) |
| `mvno` | `String` | Yes | Requested MVNO; null if request malformed before parsing |
| `msisdn` | `String` | Yes | **Masked** MSISDN (see masking rules) |
| `callerIp` | `String` | No | Client IP from `X-Forwarded-For` or remote address |
| `credentialId` | `String` | Yes | API key ID or OAuth2 client_id; null if unauthenticated |
| `httpMethod` | `String` | No | HTTP method (GET) |
| `endpoint` | `String` | No | Request path (e.g., `/api/v1/devices`) |
| `responseStatus` | `Integer` | No | HTTP response status code |
| `durationMs` | `Long` | No | Total request duration in milliseconds |
| `outcome` | `String` | No | One of: `SUCCESS`, `CACHE_HIT`, `SUBSCRIBER_NOT_FOUND`, `VENDOR_ERROR`, `AUTH_FAILURE`, `VALIDATION_FAILURE`, `INTERNAL_ERROR` |

**Serialized JSON example (audit stream):**
```json
{
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2024-01-15T10:30:00.123Z",
  "mvno": "MVNO-A",
  "msisdn": "+1212****1234",
  "callerIp": "10.0.0.1",
  "credentialId": "apikey-mvno-a-prod-01",
  "httpMethod": "GET",
  "endpoint": "/api/v1/devices",
  "responseStatus": 200,
  "durationMs": 142,
  "outcome": "SUCCESS"
}
```

---

## Vendor Request/Response DTOs

### `VendorDeviceRequest`

Outbound request sent to the third-party vendor backend. Structure determined by vendor contract (externalized per MVNO).

| Field | Type | Required | Description |
|---|---|---|---|
| `msisdn` | `String` | Yes | E.164 MSISDN to look up |
| `mvnoId` | `String` | Yes | MVNO identifier as recognized by vendor |
| `requestId` | `String` | No | Correlation ID passed as vendor tracing header |

**Note**: Vendor authentication is via a separate request header (Bearer token or API key), configured in `VendorProperties`. The request body or query params contain only the lookup keys.

---

### `VendorDeviceResponse`

Raw response received from the third-party vendor backend. All fields are nullable since vendor data completeness varies.

| Field | Type | Nullable | Description |
|---|---|---|---|
| `subscriberId` | `String` | No | Vendor's internal subscriber reference |
| `msisdn` | `String` | No | MSISDN echoed by vendor |
| `deviceName` | `String` | Yes | Human-readable device name |
| `manufacturer` | `String` | Yes | Device manufacturer name |
| `modelNumber` | `String` | Yes | Device model identifier |
| `volteEnabled` | `Boolean` | Yes | VoLTE capability flag |
| `imei` | `String` | Yes | 15-digit IMEI |
| `imsi` | `String` | Yes | 15-digit IMSI |
| `attributes` | `Map<String, Object>` | Yes | Vendor-specific extended key-value pairs |
| `status` | `String` | No | Vendor status code (e.g., "ACTIVE", "SUSPENDED") |

**Vendor field mapping to canonical response:**

| Vendor Field | Canonical Field | Transformation |
|---|---|---|
| `deviceName` | `deviceName` | Direct copy |
| `manufacturer` | `make` | Direct copy |
| `modelNumber` | `model` | Direct copy |
| `volteEnabled` | `hasVolte` | Direct copy |
| `imei` | `imei` | Direct copy |
| `imsi` | `imsi` | Direct copy |
| `attributes` | `additionalAttributes` | Direct copy (Map<String, Object>) |

---

## MVNO Credential Model

### `MvnoCredential`

Represents one MVNO's authentication configuration. Loaded at application startup from Kubernetes Secrets (injected as environment variables or mounted config file). **Not stored in a database.**

| Field | Type | Description |
|---|---|---|
| `mvnoId` | `String` | Unique MVNO identifier; must match `mvno` request parameter (case-insensitive) |
| `apiKeyHash` | `String` | BCrypt hash of the raw API key (raw key never stored) |
| `credentialId` | `String` | Human-readable key identifier for audit logging (e.g., "apikey-mvno-a-prod-01") |
| `enabled` | `boolean` | If `false`, key is revoked; authentication fails without requiring secret rotation |

**Configuration source (`application.yaml` or mounted Secret):**
```yaml
device-details:
  credentials:
    - mvnoId: MVNO-A
      apiKeyHash: "$2a$12$..."     # BCrypt hash
      credentialId: "apikey-mvno-a-prod-01"
      enabled: true
    - mvnoId: MVNO-B
      apiKeyHash: "$2a$12$..."
      credentialId: "apikey-mvno-b-prod-01"
      enabled: true
```

**OAuth2 credential model**: MVNO identity is extracted from the JWT `mvno` claim (custom claim) or `sub` claim. The JWKS endpoint URL is configured per-environment in `application.yaml`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://auth.telecom.internal/.well-known/jwks.json
```

---

## Cache Data Model

Cache is **disabled by default** and enabled per deployment via `device-details.cache.enabled=true`.

### Cache Configuration

| Property | Default | Description |
|---|---|---|
| `device-details.cache.enabled` | `false` | Master switch to enable/disable caching |
| `device-details.cache.ttl-seconds` | `300` | Time-to-live in seconds per entry |
| `device-details.cache.max-size` | `10000` | Maximum number of entries before LRU eviction |

### Cache Structure

| Attribute | Value |
|---|---|
| Cache name | `deviceDetails` |
| Key type | `String` |
| Key format | `"<mvno>:<msisdn>"` (e.g., `"MVNO-A:+12125551234"`) |
| Value type | `DeviceDetailsResponse` |
| Eviction policy | Size-based LRU + time-based TTL (Caffeine handles both) |
| Scope | Per-pod in-process (not distributed) |
| Persistence | None — lost on pod restart; acceptable given TTL |

**Cache miss flow**: `DeviceDetailsService` checks cache → miss → calls `VendorClient` → populates cache with result → returns to caller.

**Cache hit flow**: `DeviceDetailsService` checks cache → hit → returns cached `DeviceDetailsResponse` with original `correlationId` replaced by current request's `correlationId` → audit log records `outcome=CACHE_HIT`.

---

## PII Classification and Masking Rules

| Field | Location | PII Classification | Masking Rule | Applies To |
|---|---|---|---|---|
| `msisdn` | Request param, Audit log | PII — Mobile Identity | Retain country code + last 4 digits; mask middle with `****`. Example: `+12125551234` → `+1212****1234` | Audit log only; NOT logged in application logs or traces |
| `imei` | Response body | PII — Equipment Identity | **Not logged** in audit stream, application logs, or trace attributes | Excluded from all log/trace output |
| `imsi` | Response body | PII — Subscriber Identity | **Not logged** in audit stream, application logs, or trace attributes | Excluded from all log/trace output |
| `callerIp` | Audit log | Potentially PII | Logged as-is in audit stream; not included in application or trace logs | Audit log only |
| `credentialId` | Audit log | Sensitive — Key ID | Key ID (not hash, not raw key) logged for correlation | Audit log only |
| `apiKeyHash` | Config/Secret | Sensitive | BCrypt hash stored; raw key never logged or stored | Config only |

### MSISDN Masking Algorithm

```
Input:  +12125551234
Step 1: Extract country code prefix (1–3 digits after +)
Step 2: Extract last 4 digits of subscriber number
Step 3: Replace digits between country code and last 4 with ****
Output: +1212****1234

Edge cases:
- MSISDN shorter than 8 digits: mask all subscriber digits → +<cc>****
- Null/blank MSISDN: log as "<masked>"
```

### Logging Constraints

- Application logs (INFO/DEBUG): contain only `correlationId`, `mvno`, HTTP status, duration — **no MSISDN, no IMEI, no IMSI**
- Distributed traces (OpenTelemetry spans): span attributes include `mvno`, `http.status_code`, `vendor.status` — **no MSISDN, no IMEI, no IMSI**
- Metrics (Prometheus): labeled by `mvno`, `outcome`, `status_code` — **no MSISDN, no IMEI, no IMSI**
- Audit log (dedicated stream): contains masked MSISDN; never contains raw IMEI or IMSI
