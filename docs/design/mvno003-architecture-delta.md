# Architecture Delta — MVNO-003: Device Compatibility Check

**Base Architecture:** MVNO-001 `docs/design/architecture.md` (unchanged)
**Change Type:** Additive — one new endpoint, two new service classes, one config extension, no infrastructure changes

---

## New Components

### 1. `DeviceCompatibilityController` (api layer)
- Handles `GET /api/v1/devices/{imei}/compatibility`
- Validates path variable `imei` (15-digit pattern) and query param `mvno` (`@NotBlank`)
- Delegates to `DeviceCompatibilityService`
- Follows identical MDC / correlation-ID header pattern as `DeviceDetailsController`

### 2. `DeviceCompatibilityService` (service layer)
- Calls `TenantIsolationService.assertIdentityMatch(mvno)` for tenant enforcement
- Calls `VendorClient` to fetch device specifications (reuses existing client — no new HTTP calls)
- Calls `MvnoBandCatalogueService` to look up required bands for the MVNO
- Performs band intersection logic and returns `DeviceCompatibilityResponse`
- Emits Micrometer counters: `device.compatibility.check.compatible`, `.incompatible`, `.vendor_error`

### 3. `MvnoBandCatalogueService` (service layer)
- Reads MVNO → band list mappings from `application.yml` via `@ConfigurationProperties`
- Returns `List<String>` of required bands for a given MVNO ID
- Throws `UnknownMvnoException` (→ 400) if the MVNO ID has no configured band list

### 4. `BandCatalogueProperties` (config)
- `@ConfigurationProperties(prefix = "mvno.band-requirements")`
- Maps MVNO IDs to their required band lists

### 5. DTOs
- `DeviceCompatibilityResponse` — `{imei, mvno, compatible, supportedBands, requiredBands, matchedBands, reason?}`

---

## Filter Chain (unchanged — additive only)

```
CorrelationIdFilter
  → AuditLogFilter         (logs masked IMEI + COMPATIBLE/INCOMPATIBLE outcome)
  → ApiKeyAuthFilter / JwtAuthenticationConverter
  → TenantIsolationService
  → DeviceCompatibilityController
      → DeviceCompatibilityService
          → TenantIsolationService.assertIdentityMatch()
          → VendorClient             (existing — fetches device specs incl. supported bands)
          → MvnoBandCatalogueService (new — reads from application.yml)
          → band intersection logic
```

---

## Configuration Extension (`application.yml`)

```yaml
mvno:
  band-requirements:
    mvno-a:
      - B1
      - B3
      - B7
      - B20
    mvno-b:
      - B1
      - B3
      - B28
      - B40
```

No Java code change is needed to add or update an MVNO's bands — only the config map.

---

## Data Flow — Check Compatibility

```
Client
  │  GET /api/v1/devices/490154203237518/compatibility?mvno=mvno-a
  │  X-API-Key: <key>
  ▼
CorrelationIdFilter  →  AuditLogFilter  →  ApiKeyAuthFilter  →  TenantIsolationService
  ▼
DeviceCompatibilityController
  imei path-var validated: @Pattern(regexp="\\d{15}")
  mvno query-param validated: @NotBlank
  ▼
DeviceCompatibilityService
  assertIdentityMatch("mvno-a")           // tenant isolation
  vendorClient.fetchDeviceDetails(imei)  // get supportedBands from vendor response
  mvnoBandCatalogueService.getBands("mvno-a")  // ["B1","B3","B7","B20"]
  matchedBands = intersection(supportedBands, requiredBands)
  compatible   = matchedBands.containsAll(requiredBands)
  counter.increment()
  ▼
DeviceCompatibilityController
  → HTTP 200  DeviceCompatibilityResponse
```

---

## No-Change Inventory

| Component                | Changed? | Note                                      |
|--------------------------|----------|-------------------------------------------|
| VendorClient             | No       | Reused as-is; `supportedBands` already in vendor response |
| DeviceDetailsService     | No       |                                           |
| DeviceDetailsController  | No       |                                           |
| SecurityConfig           | No       |                                           |
| GlobalExceptionHandler   | No       | Existing `@ControllerAdvice` handles new 400/403/503 |
| application.yml          | **Yes**  | Adds `mvno.band-requirements` block only  |
| pom.xml                  | No       |                                           |
| Dockerfile               | No       |                                           |
| CI workflow              | No       |                                           |
