# Non-Functional Requirements — MVNO-003: Device Compatibility Check

## NFR-001: Performance
- The endpoint shall respond within **200 ms** at P99 under 300 concurrent requests.
- The vendor backend call is already protected by the existing Resilience4j circuit breaker and timeout config — no additional resilience layer is required.

## NFR-002: Availability
- Shares the service's existing **99.9% availability SLA**.
- If the vendor backend is unavailable, the endpoint returns HTTP 503 (same as the existing device details endpoint).

## NFR-003: Configuration
- MVNO band requirements must be **externalised** in `application.yml` — no hardcoded band lists in Java source.
- Adding a new MVNO or updating bands must not require a code change or redeployment (config map update only in Kubernetes).

## NFR-004: Security
- IMEI values must be masked in all logs (last 4 digits only).
- The `mvno` parameter must be validated against the authenticated identity (no cross-tenant reads).

## NFR-005: Observability
- Expose Micrometer counters:
  - `device.compatibility.check.compatible`
  - `device.compatibility.check.incompatible`
  - `device.compatibility.check.vendor_error`

## NFR-006: Backwards Compatibility
- Purely additive change. No existing endpoints, DTOs, schemas, or Spring beans are modified.

## NFR-007: Test Coverage
- Unit test coverage for compatibility logic ≥ 90 %.
- At least one integration test covering the full HTTP request/response cycle (with mocked vendor).
