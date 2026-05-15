# Acceptance Criteria — MVNO-002: IMEI Validation Endpoint

## AC-001: Happy Path — Valid IMEI
- **Given** an authenticated request with a valid 15-digit IMEI that passes the Luhn check
- **When** `POST /devices/validate-imei` is called with `{"imei": "490154203237518"}`
- **Then** HTTP 200 is returned with body `{"imei": "490154203237518", "valid": true}`

## AC-002: Invalid IMEI (Luhn Fail)
- **Given** an authenticated request with a 15-digit IMEI that fails the Luhn check
- **When** `POST /devices/validate-imei` is called with `{"imei": "490154203237519"}`
- **Then** HTTP 200 is returned with body `{"imei": "490154203237519", "valid": false, "reason": "Failed Luhn check"}`

## AC-003: Wrong Length
- **Given** an IMEI string that is not exactly 15 digits
- **When** `POST /devices/validate-imei` is called
- **Then** HTTP 400 is returned with a JSON error body containing `"message"` explaining the length requirement

## AC-004: Non-Numeric Input
- **Given** an IMEI string containing non-digit characters
- **When** `POST /devices/validate-imei` is called
- **Then** HTTP 400 is returned with `"message"` indicating the IMEI must contain digits only

## AC-005: Unauthenticated Request
- **Given** a request with no `X-API-Key` header and no `Authorization` header
- **When** `POST /devices/validate-imei` is called
- **Then** HTTP 401 is returned

## AC-006: Audit Log Entry
- **Given** any authenticated call to `POST /devices/validate-imei`
- **Then** an audit log entry is written containing tenant ID, masked IMEI (last 4 digits only), and the validation outcome

## AC-007: Metrics Counters
- **Given** the service is running
- **When** validation calls are made
- **Then** `GET /actuator/metrics/imei.validation.success` and `/imei.validation.failure` reflect the correct counts

## AC-008: Correlation ID
- **Given** a request with `X-Correlation-ID: abc-123`
- **When** `POST /devices/validate-imei` is called
- **Then** the response includes `X-Correlation-ID: abc-123`
