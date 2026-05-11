# Acceptance Criteria — device-details-service (MVNO-001)

**Ticket:** MVNO-001
**Stage:** 1 — Requirements
**Version:** 1.0.0
**Status:** Draft

---

> Each AC entry references its parent User Story (US-XX) and is linked to the relevant Functional Requirement (FR-XX) or Non-Functional Requirement (NFR-XX). All criteria are written to be directly testable in automated integration or contract tests.

---

## AC-01 (linked to US-01 — Device Query: Success Path)

**FR Reference:** FR-01, FR-04, FR-03

### AC-01.1 — Successful device query returns HTTP 200 with canonical response body

- **Given** the requesting system is authenticated as MVNO "acme-mobile"
  **And** the MSISDN "+14155551234" belongs to a subscriber of "acme-mobile" in the vendor backend
  **When** `GET /api/v1/devices?msisdn=%2B14155551234&mvno=acme-mobile` is called with valid credentials
  **Then** the response status is `200 OK`
  **And** the response `Content-Type` is `application/json`
  **And** the response body contains all canonical fields: `msisdn`, `mvno`, `deviceName`, `make`, `model`, `hasVolte`, `imei`, `imsi`
  **And** null vendor fields are represented as JSON `null` (not absent)
  **And** `msisdn` in the response equals `"+14155551234"`
  **And** `mvno` in the response equals `"acme-mobile"`

### AC-01.2 — Additional vendor fields are included in additionalAttributes

- **Given** the vendor backend returns fields beyond the canonical set
  **When** a successful device query is processed
  **Then** the response body contains an `additionalAttributes` object with the extra fields as key-value pairs
  **And** no vendor-internal metadata or system-internal fields appear at the root of the response body

### AC-01.3 — Correlation ID is returned in response header

- **Given** any successful device query request
  **When** the response is returned
  **Then** the response contains the `X-Correlation-Id` header
  **And** the value matches the `X-Correlation-Id` provided in the request (if present), or is a valid UUID (if not provided)

---

## AC-02 (linked to US-01 — Device Query: Subscriber Not Found)

**FR Reference:** FR-05

### AC-02.1 — Unknown MSISDN returns HTTP 404 with structured error

- **Given** the authenticated MVNO is "acme-mobile"
  **And** the MSISDN "+19999999999" has no record in the vendor backend for "acme-mobile"
  **When** `GET /api/v1/devices?msisdn=%2B19999999999&mvno=acme-mobile` is called
  **Then** the response status is `404 Not Found`
  **And** the response body contains `"errorCode": "SUBSCRIBER_NOT_FOUND"`
  **And** the response body contains a non-empty `"message"` field in human-readable English
  **And** the response body does NOT contain any device data fields
  **And** the response body does NOT contain any vendor-internal error codes or stack traces

### AC-02.2 — Subscriber not found event is audit logged

- **Given** a 404 (subscriber not found) response is returned
  **When** the audit log is inspected
  **Then** an audit entry exists with `outcome: "NOT_FOUND"`, the masked MSISDN, and the `correlationId`

---

## AC-03 (linked to US-01 — Device Query: Input Validation)

**FR Reference:** FR-08

### AC-03.1 — Malformed MSISDN returns HTTP 400

- **Given** any authenticated MVNO partner
  **When** a request is sent with MSISDN "12345" (missing `+` prefix and invalid length)
  **Then** the response status is `400 Bad Request`
  **And** the response body contains `"errorCode": "INVALID_MSISDN"`
  **And** the response body contains a `"violations"` array with at least one entry identifying the `msisdn` field

### AC-03.2 — Missing MSISDN parameter returns HTTP 400

- **Given** any authenticated MVNO partner
  **When** a request is sent with the `msisdn` query parameter absent
  **Then** the response status is `400 Bad Request`
  **And** the response body contains a `"violations"` array referencing the `msisdn` field

### AC-03.3 — Missing MVNO name parameter returns HTTP 400

- **Given** any authenticated MVNO partner
  **When** a request is sent with the `mvno` query parameter absent
  **Then** the response status is `400 Bad Request`
  **And** the response body contains a `"violations"` array referencing the `mvno` field

### AC-03.4 — MVNO name with special characters returns HTTP 400

- **Given** any authenticated MVNO partner
  **When** a request is sent with `mvno=acme!mobile` (disallowed character `!`)
  **Then** the response status is `400 Bad Request`
  **And** the response body contains `"errorCode": "INVALID_MVNO_NAME"`

---

## AC-04 (linked to US-01 — Device Query: Vendor Unavailable)

**FR Reference:** FR-06, FR-12

### AC-04.1 — Vendor backend unavailability returns HTTP 503 after retry exhaustion

- **Given** the vendor backend is unreachable or returns HTTP 5xx consistently
  **When** a valid device query is submitted
  **And** the service exhausts the configured retry attempts
  **Then** the response status is `503 Service Unavailable`
  **And** the response body contains `"errorCode": "VENDOR_UNAVAILABLE"`
  **And** the response body does NOT propagate vendor HTTP status codes or internal error messages
  **And** the `vendor_backend_errors_total` metric counter is incremented

### AC-04.2 — Vendor backend timeout triggers retry then 503

- **Given** the vendor backend responds after the configured timeout threshold
  **When** a valid device query is submitted
  **Then** the service retries the vendor call the configured number of times
  **And** after all retries are exhausted, the response status is `503 Service Unavailable`
  **And** the `durationMs` in the audit log reflects the total time including retry attempts

### AC-04.3 — Circuit breaker opens after threshold failures

- **Given** the vendor backend has returned errors exceeding the circuit breaker failure threshold
  **When** a subsequent device query is submitted
  **Then** the service does NOT attempt to call the vendor backend (circuit is open)
  **And** the response status is `503 Service Unavailable` returned immediately
  **And** the `circuit_breaker_state` metric equals `1` (open)

---

## AC-05 (linked to US-02 — Tenant Isolation)

**FR Reference:** FR-02

### AC-05.1 — MVNO cannot query another MVNO's subscribers

- **Given** the authenticated identity is MVNO "acme-mobile"
  **When** a request is submitted with `mvno=rival-mobile`
  **Then** the response status is `403 Forbidden`
  **And** the response body contains `"errorCode": "ACCESS_DENIED"`
  **And** NO vendor backend call is made for this request
  **And** NO device data is present in the response

### AC-05.2 — MSISDN belonging to a different MVNO returns 404 (not 403)

- **Given** the authenticated identity is MVNO "acme-mobile"
  **And** MSISDN "+15550001111" belongs to "rival-mobile" in the vendor backend
  **When** a request is submitted with `mvno=acme-mobile` and `msisdn=+15550001111`
  **Then** the response status is `404 Not Found` with `errorCode: "SUBSCRIBER_NOT_FOUND"`
  **And** the response does NOT reveal that the subscriber exists in another MVNO

### AC-05.3 — Tenant isolation is enforced before any vendor call

- **Given** a tenant mismatch is detected (authenticated MVNO ≠ requested MVNO)
  **When** the request is rejected with 403
  **Then** no outbound call to the vendor backend occurs for that request (verifiable via vendor call metrics)

---

## AC-06 (linked to US-03 — API Key Authentication)

**FR Reference:** FR-11

### AC-06.1 — Valid API key results in successful authentication

- **Given** a valid, active API key for MVNO "acme-mobile" is submitted in `X-API-Key` header
  **When** a device query request is processed
  **Then** the service resolves the MVNO identity as "acme-mobile" and proceeds to authorization and processing

### AC-06.2 — Invalid API key returns HTTP 401

- **Given** an invalid or expired API key is submitted in `X-API-Key` header
  **When** a device query request is sent
  **Then** the response status is `401 Unauthorized`
  **And** the response body contains `"errorCode": "AUTHENTICATION_FAILED"`
  **And** no device data is returned

### AC-06.3 — Absent credentials return HTTP 401

- **Given** no `X-API-Key` and no `Authorization` header is present in the request
  **When** a device query request is sent
  **Then** the response status is `401 Unauthorized`
  **And** the response body contains `"errorCode": "AUTHENTICATION_FAILED"`

---

## AC-07 (linked to US-04 — OAuth2 Authentication)

**FR Reference:** FR-11

### AC-07.1 — Valid JWT bearer token results in successful authentication

- **Given** a valid, non-expired JWT issued by the configured Authorization Server with the correct MVNO claim
  **When** a device query is submitted with `Authorization: Bearer <token>`
  **Then** the service extracts the MVNO identity from the JWT claim and proceeds to authorization

### AC-07.2 — Expired JWT returns HTTP 401

- **Given** an expired JWT bearer token (past `exp` claim)
  **When** a device query is submitted
  **Then** the response status is `401 Unauthorized` with `"errorCode": "AUTHENTICATION_FAILED"`

### AC-07.3 — Untrusted issuer JWT returns HTTP 401

- **Given** a JWT signed by a key not in the configured JWKS
  **When** a device query is submitted
  **Then** the response status is `401 Unauthorized` with `"errorCode": "AUTHENTICATION_FAILED"`

### AC-07.4 — JWT missing MVNO claim returns HTTP 403

- **Given** a valid JWT that does not contain the required MVNO identity claim
  **When** a device query is submitted
  **Then** the response status is `403 Forbidden` with `"errorCode": "ACCESS_DENIED"`

---

## AC-08 (linked to US-05 — Operations Device Lookup)

**FR Reference:** FR-01, FR-05, FR-06

### AC-08.1 — Successful lookup returns all canonical fields including hasVolte

- **Given** a valid operations credential for MVNO "acme-mobile"
  **And** the subscriber exists with a device record including VoLTE support status
  **When** a device query is made via the operations tool
  **Then** the response contains `hasVolte` as a boolean (`true` or `false`), never null when provided by vendor

### AC-08.2 — Subscriber not found produces a clear non-data response

- **Given** the queried MSISDN has no vendor record
  **When** the operations tool receives the API response
  **Then** the HTTP status is `404` and `errorCode` is `"SUBSCRIBER_NOT_FOUND"` with no device fields in the body

### AC-08.3 — Vendor unavailability produces 503 with no partial data

- **Given** the vendor backend is unavailable
  **When** the operations tool receives the API response
  **Then** the HTTP status is `503` and `errorCode` is `"VENDOR_UNAVAILABLE"`
  **And** the response body contains NO partial device fields (no mix of data and error)

---

## AC-09 (linked to US-06 — Credential Management)

**FR Reference:** FR-11

### AC-09.1 — Newly provisioned API key is functional within propagation window

- **Given** a new API key for MVNO "new-mvno" is added to the credential store
  **When** a device query is submitted using that API key (after credential propagation)
  **Then** the service authenticates and processes the request successfully

### AC-09.2 — Revoked API key is rejected within 60 seconds

- **Given** an API key for MVNO "acme-mobile" is revoked in the credential store
  **When** a device query is submitted using the revoked key, after ≤ 60 seconds
  **Then** the response status is `401 Unauthorized` with `"errorCode": "AUTHENTICATION_FAILED"`

### AC-09.3 — Rotated API key — new key works, old key rejected

- **Given** the API key for MVNO "acme-mobile" is rotated (old revoked, new issued)
  **When** a query is sent with the new key
  **Then** the response is `200 OK` (or appropriate successful response)
  **And** a query with the old key returns `401 Unauthorized`

---

## AC-10 (linked to US-07 — Service Health Monitoring)

**FR Reference:** FR-10
**NFR Reference:** NFR-01, NFR-07

### AC-10.1 — Liveness probe returns 200 when service is running

- **Given** the application process is running and not deadlocked
  **When** `GET /actuator/health/liveness` is called without authentication
  **Then** the response status is `200 OK`
  **And** the response body contains `{"status": "UP"}`
  **And** no credentials, PII, or sensitive configuration values are present in the response body

### AC-10.2 — Readiness probe returns 503 during startup

- **Given** the application is still initializing
  **When** `GET /actuator/health/readiness` is called
  **Then** the response status is NOT `200 OK` (expected `503` or `503`-equivalent)

### AC-10.3 — Readiness probe returns 200 when service is fully initialized

- **Given** the application is fully started and ready to process requests
  **When** `GET /actuator/health/readiness` is called
  **Then** the response status is `200 OK`
  **And** the response body contains `{"status": "UP"}`

### AC-10.4 — Prometheus metrics endpoint returns valid exposition format

- **Given** the service is running
  **When** `GET /actuator/prometheus` is scraped by Prometheus (no authentication required from internal network)
  **Then** the response status is `200 OK`
  **And** the response body is valid Prometheus text exposition format (version 0.0.4 or OpenMetrics)
  **And** the response contains all mandatory metrics defined in NFR-07

---

## AC-11 (linked to US-08 — Observability and Alerting)

**NFR Reference:** NFR-07, NFR-08, NFR-09

### AC-11.1 — Distributed trace contains root span and vendor child span

- **Given** a device query request is processed successfully
  **When** the trace is retrieved from the tracing backend
  **Then** the trace contains a root span labeled for the inbound HTTP request
  **And** the trace contains a child span labeled for the vendor backend HTTP call
  **And** both spans share the same trace ID
  **And** neither span contains `msisdn`, `imei`, or `imsi` as attribute values

### AC-11.2 — Circuit breaker metric transitions on threshold breach

- **Given** the vendor backend error rate exceeds the configured circuit breaker threshold within the evaluation window
  **When** the `circuit_breaker_state` metric is queried
  **Then** the metric value is `1` (open) within the configured circuit breaker evaluation window
  **And** the metric transitions back to `0` (closed) after the vendor backend recovers and the half-open probe succeeds

### AC-11.3 — Authentication failure is logged at WARN level with no credential values

- **Given** a request with invalid credentials is submitted
  **When** the application log is inspected
  **Then** an entry at `WARN` level exists containing `correlationId`, caller IP, and `errorCode`
  **And** the log entry does NOT contain plaintext API keys, passwords, or JWT values

### AC-11.4 — p99 latency is observable from histogram metrics

- **Given** requests are being processed under load
  **When** the `http_server_requests_seconds` histogram is queried
  **Then** p50, p95, and p99 percentiles can be computed from the histogram bucket data

---

## AC-12 (linked to US-09 — Audit Trail)

**FR Reference:** FR-09

### AC-12.1 — Every request produces a structured audit log entry

- **Given** any request to the device query endpoint (successful or failed)
  **When** the audit log stream is inspected after the request completes
  **Then** a single JSON audit entry is present containing all mandatory fields: `correlationId`, `timestamp`, `mvno`, `msisdn` (masked), `callerIp`, `credentialId`, `httpMethod`, `endpoint`, `responseStatus`, `durationMs`, `outcome`

### AC-12.2 — MSISDN is masked in audit log entries

- **Given** a request with MSISDN "+14155551234" is processed
  **When** the audit log entry for that request is inspected
  **Then** the `msisdn` field value is `"******1234"` (or equivalent masking with last 4 digits visible)
  **And** the full unmasked MSISDN does NOT appear anywhere in the audit log entry

### AC-12.3 — Auth failure events are captured in audit log

- **Given** a request results in HTTP 401 or 403
  **When** the audit log is inspected
  **Then** the audit entry for that request has `outcome` set to `"AUTH_FAILURE"`
  **And** `responseStatus` matches the actual HTTP status code returned (401 or 403)

### AC-12.4 — Audit log write failure does not fail the API response

- **Given** the audit log write mechanism encounters a transient error
  **When** the API request is being processed
  **Then** the API response is returned to the caller normally (2xx or appropriate error code based on business logic)
  **And** the audit write failure is recorded in the application error log stream

---

## AC-13 (linked to US-10 — Correlation ID Propagation)

**FR Reference:** FR-14

### AC-13.1 — Supplied correlation ID is echoed in response

- **Given** a request is sent with `X-Correlation-Id: test-trace-xyz-001`
  **When** the service returns a response
  **Then** the response contains the header `X-Correlation-Id: test-trace-xyz-001`

### AC-13.2 — Service generates UUID when no correlation ID is supplied

- **Given** a request is sent WITHOUT an `X-Correlation-Id` header
  **When** the service returns a response
  **Then** the response contains an `X-Correlation-Id` header with a valid UUID (format: 8-4-4-4-12 hexadecimal, version 4)

### AC-13.3 — All log entries for a request share the same correlation ID

- **Given** a request with `X-Correlation-Id: trace-audit-test-007` is processed
  **When** all application log entries and the audit log entry for that request are inspected
  **Then** every entry contains `"correlationId": "trace-audit-test-007"`
  **And** no log entry for that request contains a different correlation ID

### AC-13.4 — Correlation ID is forwarded to vendor backend call

- **Given** a request with `X-Correlation-Id: upstream-id-123` is processed
  **When** the outbound vendor backend call is made
  **Then** the vendor call includes a tracing/correlation header containing `upstream-id-123` (in the vendor-supported header format)

---

## AC-14 (linked to NFR-02 — Latency Targets)

**NFR Reference:** NFR-02

### AC-14.1 — p99 latency remains ≤ 1,000 ms under steady-state load

- **Given** the service is under steady-state load (≥ 500 RPS) with the vendor backend healthy and responding within its SLA
  **When** latency percentiles are computed from the `http_server_requests_seconds` histogram over a 5-minute window
  **Then** p50 ≤ 200 ms, p95 ≤ 500 ms, and p99 ≤ 1,000 ms

---

## AC-15 (linked to NFR-05 — Security)

**NFR Reference:** NFR-05

### AC-15.1 — TLS is enforced on all production-facing endpoints

- **Given** the service is deployed in a production environment
  **When** an HTTP (non-TLS) connection is attempted to any API endpoint
  **Then** the connection is rejected or redirected to HTTPS
  **And** TLS version negotiated is 1.2 or higher

### AC-15.2 — Security response headers are present on all API responses

- **Given** any API response is returned
  **When** the response headers are inspected
  **Then** the headers include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and `Strict-Transport-Security` with an appropriate `max-age` value

### AC-15.3 — PII fields are absent from metric label values

- **Given** the service is processing requests with various MSISDNs
  **When** the Prometheus metrics endpoint is scraped
  **Then** no metric label value contains an MSISDN, IMSI, or IMEI value
