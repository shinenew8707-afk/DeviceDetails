# User Stories — MVNO-002: IMEI Validation Endpoint

## US-001: MVNO Operator Validates a Device IMEI Before Provisioning

> **As an** MVNO operator,
> **I want to** validate an IMEI number before provisioning a device on my network,
> **So that** I can reject invalid or tampered devices early in the onboarding flow.

**Acceptance Criteria:** Given a valid API key and a well-formed 15-digit IMEI, when I `POST /devices/validate-imei`, then I receive HTTP 200 with `"valid": true`.

---

## US-002: MVNO Operator Receives Clear Feedback on Invalid IMEI

> **As an** MVNO operator,
> **I want to** receive a descriptive error message when I submit an invalid IMEI,
> **So that** my support team can advise customers on what went wrong.

**Acceptance Criteria:** Given an IMEI that fails the Luhn check, when I `POST /devices/validate-imei`, then I receive HTTP 200 with `"valid": false` and a non-empty `"reason"` field.

---

## US-003: Unauthenticated Caller is Rejected

> **As a** security engineer,
> **I want** the validate-imei endpoint to reject unauthenticated requests,
> **So that** IMEI data is not accessible without proper credentials.

**Acceptance Criteria:** Given a request with no API key and no JWT, when I `POST /devices/validate-imei`, then I receive HTTP 401.

---

## US-004: Malformed Input is Rejected with 400

> **As an** MVNO integration developer,
> **I want** the API to return HTTP 400 for non-numeric or wrong-length inputs,
> **So that** my client code can distinguish input errors from business-logic rejections.

**Acceptance Criteria:** Given an IMEI string containing letters or fewer/more than 15 digits, when I `POST /devices/validate-imei`, then I receive HTTP 400 with a descriptive `message` field.
