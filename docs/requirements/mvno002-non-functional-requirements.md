# Non-Functional Requirements — MVNO-002: IMEI Validation Endpoint

## NFR-001: Performance
- The `POST /devices/validate-imei` endpoint shall respond within **50 ms** at the 99th percentile under a load of 500 concurrent requests.
- The Luhn algorithm computation is O(n) and must not introduce blocking I/O.

## NFR-002: Availability
- The endpoint shall share the service's existing **99.9% availability SLA**.
- No external dependency is introduced; validation is pure in-process computation.

## NFR-003: Security
- Input must be validated before processing; overly long strings (> 20 chars) are rejected immediately to prevent ReDoS.
- IMEI values must be masked in all logs (only last 4 digits visible).

## NFR-004: Observability
- Validation success/failure counts shall be exposed via the existing Spring Boot Actuator `/actuator/metrics` endpoint using Micrometer counters:
  - `imei.validation.success`
  - `imei.validation.failure`

## NFR-005: Backwards Compatibility
- This is an additive change. No existing endpoints, DTOs, or database schemas are modified.

## NFR-006: Test Coverage
- Unit test coverage for the validation logic shall be ≥ 90%.
- At least one integration test must cover the full HTTP request/response cycle.
