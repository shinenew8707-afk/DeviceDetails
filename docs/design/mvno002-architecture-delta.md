# Architecture Delta — MVNO-002: IMEI Validation Endpoint

**Base Architecture:** MVNO-001 `docs/design/architecture.md` (unchanged)
**Change Type:** Additive — new endpoint, new service class, no infrastructure changes

---

## New Components

### 1. `ImeiValidationController` (api layer)
- Handles `POST /api/v1/devices/validate-imei`
- Delegates to `ImeiValidationService`
- Reuses existing `@RestController` conventions, security filter chain, and correlation ID propagation

### 2. `ImeiValidationService` (service layer)
- Pure stateless bean containing the Luhn-algorithm implementation
- Emits Micrometer counters `imei.validation.success` and `imei.validation.failure`

### 3. DTOs
- `ImeiValidationRequest` — `{ "imei": "string" }` (Bean Validation: `@NotBlank`, `@Size(min=15,max=15)`, `@Pattern(regexp="\\d+")`)
- `ImeiValidationResponse` — `{ "imei": "string", "valid": boolean, "reason": "string?" }`

---

## Filter Chain (unchanged — additive only)

```
CorrelationIdFilter
  → AuditLogFilter         (logs masked IMEI + outcome for validate-imei calls)
  → ApiKeyAuthFilter / JwtAuthenticationConverter
  → TenantIsolationService
  → ImeiValidationController
      → ImeiValidationService  (pure computation, no I/O)
```

No changes to `VendorClient`, `ResponseMapper`, or any existing service class.

---

## Data Flow — Validate IMEI

```
Client
  │  POST /api/v1/devices/validate-imei
  │  { "imei": "490154203237518" }
  │  X-API-Key: <key>
  │  X-Correlation-ID: <uuid>
  ▼
CorrelationIdFilter          propagates / generates X-Correlation-ID
  ▼
AuditLogFilter               records tenant, masked IMEI (last 4 digits), outcome
  ▼
ApiKeyAuthFilter             resolves MVNO tenant from API key
  ▼
TenantIsolationService       verifies tenant is active
  ▼
ImeiValidationController     deserialises + validates request body
  ▼
ImeiValidationService        runs Luhn check, updates counters
  ▼
ImeiValidationController     serialises ImeiValidationResponse
  ▼
Client
  HTTP 200 { "imei": "490154203237518", "valid": true }
```

---

## No-Change Inventory

| Component                | Changed? |
|--------------------------|----------|
| VendorClient             | No       |
| DeviceDetailsService     | No       |
| DeviceDetailsController  | No       |
| SecurityConfig           | No       |
| MvnoCredentialStore      | No       |
| ResponseMapper           | No       |
| GlobalExceptionHandler   | No — existing `@ControllerAdvice` handles new 400s automatically |
| application.yml          | No       |
| Dockerfile               | No       |
| pom.xml                  | No       |
| CI workflow              | No       |
