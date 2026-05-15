# Functional Requirements — MVNO-003: Device Compatibility Check

## FR-001: Device Compatibility API

**Description:** The Device Details Service shall expose a REST endpoint that checks whether a specific device (identified by IMEI) is compatible with a given MVNO's network, based on supported frequency bands.

**Endpoint:** `GET /api/v1/devices/{imei}/compatibility`

**Request Parameters:**
- `imei` (path, required) — 15-digit IMEI of the device
- `mvno` (query, required) — MVNO tenant identifier

**Response:** A JSON object containing:
- `imei` (string) — echoed from path
- `mvno` (string) — echoed from query parameter
- `compatible` (boolean) — overall compatibility result
- `supportedBands` (array of strings) — bands the device supports
- `requiredBands` (array of strings) — bands required by the MVNO
- `matchedBands` (array of strings) — intersection of device and MVNO bands
- `reason` (string, optional) — human-readable explanation when `compatible` is false

## FR-002: Band Catalogue Lookup

**Description:** The service shall maintain a configurable catalogue of MVNO network band requirements. Each MVNO tenant has an associated set of required frequency bands (e.g., `B1`, `B3`, `B7`, `B20`, `B28`). This catalogue shall be externalised in `application.yml` under `mvno.band-requirements`.

## FR-003: Device Band Lookup

**Description:** Device band information (supported bands) shall be retrieved from the existing vendor backend via the `VendorClient`, which already provides device specifications. The compatibility service shall extract the `supportedBands` field from the vendor response.

## FR-004: Compatibility Logic

**Description:** A device is considered **compatible** if it supports **all** of the MVNO's required bands. Partial overlap is returned in `matchedBands` but results in `compatible: false`.

## FR-005: Authentication & Tenant Isolation

**Description:** The endpoint shall be protected by the same API-key / JWT authentication. The authenticated MVNO identity must match the `mvno` query parameter (tenant isolation via `TenantIsolationService`).

## FR-006: Audit Logging

**Description:** Every call to the compatibility endpoint shall be captured by `AuditLogFilter`, recording tenant ID, masked IMEI (last 4 digits), and the compatibility outcome (`COMPATIBLE` / `INCOMPATIBLE`).

## FR-007: Correlation ID Propagation

**Description:** `X-Correlation-ID` shall be accepted and echoed in the response, consistent with all other endpoints.
