# Acceptance Criteria — MVNO-003: Device Compatibility Check

## AC-001: Compatible Device
- **Given** an authenticated MVNO-A request and a device IMEI whose `supportedBands` include all of MVNO-A's `requiredBands`
- **When** `GET /api/v1/devices/{imei}/compatibility?mvno=mvno-a` is called
- **Then** HTTP 200 is returned with `{"imei":"...","mvno":"mvno-a","compatible":true,"supportedBands":[...],"requiredBands":[...],"matchedBands":[...]}`

## AC-002: Incompatible Device (Partial Band Match)
- **Given** an authenticated MVNO-A request and a device that supports only a subset of MVNO-A's required bands
- **When** `GET /api/v1/devices/{imei}/compatibility?mvno=mvno-a` is called
- **Then** HTTP 200 is returned with `"compatible":false`, a non-empty `"reason"` identifying missing bands, and `"matchedBands"` containing the intersection

## AC-003: Incompatible Device (No Band Match)
- **Given** an authenticated request and a device that shares no bands with the MVNO
- **When** `GET /api/v1/devices/{imei}/compatibility?mvno=mvno-a` is called
- **Then** HTTP 200 is returned with `"compatible":false`, `"matchedBands":[]`, and `"reason":"No matching bands"`

## AC-004: Invalid IMEI Format
- **Given** an IMEI that is not 15 digits or contains non-numeric characters
- **When** `GET /api/v1/devices/{imei}/compatibility?mvno=mvno-a` is called
- **Then** HTTP 400 is returned with a JSON error body

## AC-005: Missing mvno Parameter
- **Given** a request with no `mvno` query parameter
- **When** the endpoint is called
- **Then** HTTP 400 is returned

## AC-006: Unauthenticated Request
- **Given** a request with no `X-API-Key` and no `Authorization` header
- **When** the endpoint is called
- **Then** HTTP 401 is returned

## AC-007: Cross-Tenant Access Denied
- **Given** an MVNO-A API key and `?mvno=mvno-b` in the query
- **When** the endpoint is called
- **Then** HTTP 403 is returned

## AC-008: Vendor Unavailable
- **Given** the vendor backend is down
- **When** the endpoint is called
- **Then** HTTP 503 is returned with `Retry-After: 30` header

## AC-009: Audit Log Entry
- **Given** any authenticated call to the compatibility endpoint
- **Then** an audit log entry is written with tenant ID, masked IMEI (last 4 digits), and outcome (`COMPATIBLE` or `INCOMPATIBLE`)

## AC-010: Correlation ID
- **Given** a request with `X-Correlation-ID: test-123`
- **When** the endpoint is called
- **Then** the response includes `X-Correlation-ID: test-123`

## AC-011: Micrometer Counters
- **Given** the service is running and compatibility checks are performed
- **Then** `imei.device.compatibility.check.compatible` and `device.compatibility.check.incompatible` counters increment correctly
