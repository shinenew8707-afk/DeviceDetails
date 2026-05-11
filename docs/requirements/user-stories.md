# User Stories — device-details-service (MVNO-001)

**Ticket:** MVNO-001
**Stage:** 1 — Requirements
**Version:** 1.0.0
**Status:** Draft

---

## Personas

| Persona ID | Name                  | Description                                                                                          |
|------------|-----------------------|------------------------------------------------------------------------------------------------------|
| P-01       | MVNO Partner System   | An authenticated automated system operated by an MVNO partner that queries device details via API    |
| P-02       | MVNO Operations User  | A human operator at an MVNO who uses internal tooling (backed by this service) to troubleshoot devices|
| P-03       | Platform Administrator| An internal admin responsible for managing MVNO credentials and monitoring service health            |
| P-04       | DevOps / SRE Engineer | An engineer responsible for deploying, monitoring, and maintaining the service in production          |
| P-05       | Security Auditor      | A compliance or security team member who audits access logs and validates tenant isolation            |

---

## US-01: Query Device Details by MSISDN

**As a** MVNO Partner System (P-01),
**I want to** query device details for a specific subscriber by providing the subscriber's MSISDN and my MVNO name,
**So that** I can retrieve accurate, up-to-date device information (make, model, IMEI, VoLTE capability, etc.) to support my customer-facing services and network management operations.

### Acceptance Criteria

- **Given** a valid API key or OAuth2 token for MVNO "acme-mobile" and a valid E.164 MSISDN "+14155551234" belonging to a subscriber of "acme-mobile",
  **When** I send `GET /api/v1/devices?msisdn=%2B14155551234&mvno=acme-mobile` with my credentials,
  **Then** the service returns HTTP 200 with a JSON response containing non-null values for `msisdn`, `mvno`, `deviceName`, `make`, `model`, `hasVolte`, `imei`, and `imsi` where available from the vendor.

- **Given** a valid MSISDN that the vendor backend has no record of,
  **When** I send the query request,
  **Then** the service returns HTTP 404 with `errorCode: "SUBSCRIBER_NOT_FOUND"` and a human-readable `message`.

- **Given** a request with a malformed MSISDN (e.g., "12345" without the `+` prefix),
  **When** I send the query request,
  **Then** the service returns HTTP 400 with `errorCode: "INVALID_MSISDN"` and a `violations` array identifying the invalid field.

- **Given** the vendor backend is temporarily unavailable,
  **When** I send a valid query request,
  **Then** the service returns HTTP 503 with `errorCode: "VENDOR_UNAVAILABLE"` after exhausting configured retries.

---

## US-02: Tenant Isolation — MVNO Cannot Access Other MVNO's Subscribers

**As a** MVNO Partner System (P-01) for MVNO "acme-mobile",
**I want to** be prevented from accessing device records of subscribers belonging to other MVNOs,
**So that** subscriber data confidentiality is maintained across all MVNO tenants.

### Acceptance Criteria

- **Given** a valid API key or OAuth2 token for MVNO "acme-mobile",
  **When** I send a request specifying `mvno=rival-mobile`,
  **Then** the service returns HTTP 403 with `errorCode: "ACCESS_DENIED"` and does NOT return any device data.

- **Given** a valid API key or OAuth2 token for MVNO "acme-mobile",
  **When** I send a request specifying `mvno=acme-mobile`,
  **Then** the service processes the request normally (per US-01 outcomes).

- **Given** a valid API key or OAuth2 token for MVNO "acme-mobile" and a MSISDN that exists in "rival-mobile"'s subscriber base,
  **When** I send a request specifying `mvno=acme-mobile`,
  **Then** the service returns HTTP 404 (subscriber not found within the requesting MVNO) and MUST NOT return the device data.

---

## US-03: Authentication — API Key Based Access

**As a** MVNO Partner System (P-01),
**I want to** authenticate to the device-details-service using an issued API key,
**So that** my integration does not require a full OAuth2 authorization server setup while still being cryptographically authenticated.

### Acceptance Criteria

- **Given** a valid, active API key transmitted in the `X-API-Key` header,
  **When** I send a device query request,
  **Then** the service authenticates my identity, resolves my MVNO, and processes the request.

- **Given** an invalid or revoked API key in the `X-API-Key` header,
  **When** I send a device query request,
  **Then** the service returns HTTP 401 with `errorCode: "AUTHENTICATION_FAILED"`.

- **Given** no `X-API-Key` header and no `Authorization` header is present,
  **When** I send a device query request,
  **Then** the service returns HTTP 401 with `errorCode: "AUTHENTICATION_FAILED"`.

---

## US-04: Authentication — OAuth2 Client Credentials Access

**As a** MVNO Partner System (P-01),
**I want to** authenticate using an OAuth2 client credentials flow and present a JWT bearer token,
**So that** my integration is compatible with enterprise identity and access management systems.

### Acceptance Criteria

- **Given** a valid JWT bearer token issued by the configured Authorization Server with the correct MVNO identity claim,
  **When** I send a device query request with `Authorization: Bearer <token>`,
  **Then** the service validates the token, resolves the MVNO identity from the claim, and processes the request.

- **Given** an expired JWT bearer token,
  **When** I send a device query request,
  **Then** the service returns HTTP 401 with `errorCode: "AUTHENTICATION_FAILED"`.

- **Given** a JWT bearer token signed by an untrusted issuer,
  **When** I send a device query request,
  **Then** the service returns HTTP 401 with `errorCode: "AUTHENTICATION_FAILED"`.

- **Given** a valid JWT token that does NOT contain the required MVNO identity claim,
  **When** I send a device query request,
  **Then** the service returns HTTP 403 with `errorCode: "ACCESS_DENIED"`.

---

## US-05: Operations — Human-Initiated Device Lookup

**As an** MVNO Operations User (P-02),
**I want to** query device details for a specific subscriber through an internal tool that calls this service,
**So that** I can resolve customer complaints related to device compatibility or VoLTE issues without contacting the vendor directly.

### Acceptance Criteria

- **Given** I am logged into the internal operations tool and have a valid credential for my MVNO,
  **When** I input a subscriber's MSISDN and initiate a lookup,
  **Then** the tool receives a 200 response from the service and displays all canonical device fields including `hasVolte`.

- **Given** the subscriber's MSISDN is not found,
  **When** I initiate a lookup,
  **Then** the tool displays a clear "Subscriber not found" message (derived from HTTP 404 and `errorCode: SUBSCRIBER_NOT_FOUND`).

- **Given** the vendor backend is unavailable,
  **When** I initiate a lookup,
  **Then** the tool displays a "Device backend temporarily unavailable" message (derived from HTTP 503) and does not show partial data.

---

## US-06: Platform Administrator — MVNO Credential Management

**As a** Platform Administrator (P-03),
**I want to** be able to provision, rotate, and revoke API keys or OAuth2 client credentials for MVNO partners,
**So that** I can onboard new MVNO partners and enforce credential hygiene without service downtime.

### Acceptance Criteria

- **Given** a new MVNO partner "new-mvno" requires onboarding,
  **When** the administrator provisions a new API key bound to "new-mvno" in the credential store,
  **Then** subsequent requests using that API key with `mvno=new-mvno` are authenticated and processed successfully.

- **Given** a compromised API key for MVNO "acme-mobile",
  **When** the administrator revokes that key in the credential store,
  **Then** subsequent requests using the revoked key return HTTP 401 within the credential store's propagation latency window (target: ≤ 60 seconds).

- **Given** an API key rotation for MVNO "acme-mobile" (old key revoked, new key issued),
  **When** the MVNO switches to the new key,
  **Then** requests with the new key are authenticated and processed, and requests with the old key return HTTP 401.

---

## US-07: SRE / DevOps — Service Health Monitoring

**As a** DevOps / SRE Engineer (P-04),
**I want to** query the service's health and readiness endpoints from Kubernetes and external monitoring systems,
**So that** I can detect degraded or unavailable instances and prevent traffic routing to unhealthy pods.

### Acceptance Criteria

- **Given** the service is fully started and its dependencies are reachable,
  **When** Kubernetes queries `GET /actuator/health/liveness`,
  **Then** the response is HTTP 200 with `{"status": "UP"}`.

- **Given** the service is fully started and its dependencies are reachable,
  **When** Kubernetes queries `GET /actuator/health/readiness`,
  **Then** the response is HTTP 200 with `{"status": "UP"}`.

- **Given** the service is starting up but not yet ready to serve traffic,
  **When** Kubernetes queries `GET /actuator/health/readiness`,
  **Then** the response is HTTP 503 with `{"status": "OUT_OF_SERVICE"}` or equivalent non-UP status.

- **Given** the health endpoints are queried,
  **When** any health endpoint returns a response,
  **Then** the response does NOT contain credentials, API keys, MSISDN values, or any PII.

- **Given** the service is running,
  **When** Prometheus scrapes `GET /actuator/prometheus`,
  **Then** the response contains all defined metrics (NFR-07) in valid Prometheus exposition format.

---

## US-08: SRE / DevOps — Observability and Alerting

**As a** DevOps / SRE Engineer (P-04),
**I want to** observe request latency, error rates, vendor backend health, and circuit breaker state via metrics and distributed traces,
**So that** I can detect, diagnose, and remediate incidents quickly and meet SLA obligations.

### Acceptance Criteria

- **Given** a device query request is processed,
  **When** I inspect the distributed trace,
  **Then** I can see a root span for the inbound HTTP request and a child span for the vendor backend call, linked by the same trace ID and correlation ID.

- **Given** the vendor backend experiences elevated error rates exceeding the circuit breaker threshold,
  **When** I query the `circuit_breaker_state` metric,
  **Then** the metric value changes from `0` (closed) to `1` (open) within the configured evaluation window.

- **Given** requests are being processed,
  **When** I query the `http_server_requests_seconds` histogram,
  **Then** I can compute p50, p95, and p99 latency across all requests.

- **Given** an authentication failure occurs,
  **When** I inspect application logs,
  **Then** I find a structured log entry at WARN level containing the `correlationId`, caller IP, and `errorCode`, with no plaintext credential values.

---

## US-09: Security Auditor — Audit Trail Review

**As a** Security Auditor (P-05),
**I want to** review a complete, tamper-evident audit log of all device queries made through the service,
**So that** I can verify tenant isolation compliance, detect unauthorized access attempts, and support forensic investigation.

### Acceptance Criteria

- **Given** any request is made to the device query endpoint (successful or failed),
  **When** I inspect the audit log stream,
  **Then** I find a JSON audit entry containing: `correlationId`, `timestamp`, `mvno`, masked `msisdn`, `callerIp`, `credentialId`, `httpMethod`, `endpoint`, `responseStatus`, `durationMs`, and `outcome`.

- **Given** an unauthorized access attempt (HTTP 401 or 403) is made,
  **When** I inspect the audit log,
  **Then** the entry is present with `outcome` set to `AUTH_FAILURE` and the attempted MVNO name recorded.

- **Given** an audit log entry is written,
  **When** I inspect the `msisdn` field,
  **Then** the value is masked (e.g., `******1234`) and the full MSISDN is NOT present in the audit record.

- **Given** the audit log write mechanism experiences a transient error,
  **When** the write fails,
  **Then** the failure is recorded in the application error log stream and the request continues to completion (audit write failure MUST NOT cause the API response to fail).

---

## US-10: MVNO Partner — Correlation ID for Request Tracing

**As a** MVNO Partner System (P-01),
**I want to** supply my own correlation ID in the request and receive it back in the response,
**So that** I can correlate device lookup requests in my own system logs with entries in the device-details-service logs for end-to-end troubleshooting.

### Acceptance Criteria

- **Given** I send a request with the header `X-Correlation-Id: my-system-trace-abc123`,
  **When** the service processes and responds to my request,
  **Then** the response includes the header `X-Correlation-Id: my-system-trace-abc123`.

- **Given** I send a request WITHOUT an `X-Correlation-Id` header,
  **When** the service processes and responds to my request,
  **Then** the response includes an `X-Correlation-Id` header containing a service-generated UUID.

- **Given** a correlation ID is present (supplied or generated),
  **When** I inspect the audit log and application logs for that request,
  **Then** all log entries for that request contain the same `correlationId` value.
