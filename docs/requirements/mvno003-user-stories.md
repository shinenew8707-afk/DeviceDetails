# User Stories — MVNO-003: Device Compatibility Check

## US-001: MVNO Operator Checks Device Before SIM Provisioning

> **As an** MVNO operator,
> **I want to** check whether a customer's device supports my network's frequency bands,
> **So that** I can prevent provisioning a SIM into a device that will not work on my network.

**Acceptance Criteria:** Given a valid API key, a 15-digit IMEI, and my MVNO ID, when I `GET /api/v1/devices/{imei}/compatibility?mvno=mvno-a`, I receive HTTP 200 with `compatible: true/false` and `matchedBands`.

---

## US-002: Operator Receives Actionable Incompatibility Details

> **As an** MVNO operator,
> **I want to** see exactly which bands my network requires versus which bands the device supports,
> **So that** I can explain to the customer why their device is incompatible.

**Acceptance Criteria:** When `compatible` is `false`, the response contains `requiredBands`, `supportedBands`, and a non-empty `reason` field identifying the missing bands.

---

## US-003: Unauthenticated Caller is Rejected

> **As a** security engineer,
> **I want** the compatibility endpoint to reject unauthenticated requests,
> **So that** device and network band data is not exposed without credentials.

**Acceptance Criteria:** A request with no `X-API-Key` and no `Authorization` header receives HTTP 401.

---

## US-004: Cross-Tenant Access is Denied

> **As a** security engineer,
> **I want** the MVNO in the query parameter to be validated against the authenticated identity,
> **So that** MVNO-A cannot query compatibility data scoped to MVNO-B.

**Acceptance Criteria:** An MVNO-A API key querying `?mvno=mvno-b` receives HTTP 403.

---

## US-005: Vendor Unavailability is Gracefully Handled

> **As an** MVNO integration developer,
> **I want** a clear 503 response when the vendor backend is down,
> **So that** my system can implement retry logic rather than treating it as a permanent error.

**Acceptance Criteria:** When the vendor backend is unavailable, the endpoint returns HTTP 503 with a `Retry-After: 30` header.
