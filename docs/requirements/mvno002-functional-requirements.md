# Functional Requirements — MVNO-002: IMEI Validation Endpoint

## FR-001: IMEI Validation API

**Description:** The Device Details Service shall expose a dedicated REST endpoint that validates a given IMEI (International Mobile Equipment Identity) number and returns a structured validation result.

**Details:**
- Endpoint: `POST /devices/validate-imei`
- Accepts a JSON request body containing the IMEI string.
- Validates the IMEI using the **Luhn algorithm** (ISO/IEC 7812).
- Validates that the IMEI is exactly 15 digits.
- Returns a JSON response indicating:
  - `valid` (boolean): whether the IMEI passed all checks
  - `imei` (string): the original IMEI echoed back
  - `reason` (string, optional): human-readable explanation if invalid

## FR-002: Input Sanitisation

**Description:** The endpoint shall reject inputs that are not numeric strings or exceed/fall short of 15 digits, returning a `400 Bad Request` with a descriptive error.

## FR-003: Authentication & Tenant Isolation

**Description:** The IMEI validation endpoint shall be protected by the same API-key / JWT authentication mechanism used by the existing `/devices/{imei}` endpoint. Tenant isolation rules apply — only authenticated MVNO tenants may call the endpoint.

## FR-004: Audit Logging

**Description:** Every call to `POST /devices/validate-imei` shall be captured by the existing `AuditLogFilter`, recording tenant ID, timestamp, IMEI (masked to last 4 digits), and validation outcome.

## FR-005: Correlation ID Propagation

**Description:** The `X-Correlation-ID` header shall be accepted and forwarded through the validation flow in the same manner as existing endpoints.
