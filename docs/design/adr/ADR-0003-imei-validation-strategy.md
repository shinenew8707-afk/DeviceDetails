# ADR-0003: IMEI Validation Strategy

**Status:** Accepted
**Date:** 2026-05-15
**Ticket:** MVNO-002
**Deciders:** Platform Engineering

---

## Context

MVNO operators need to validate IMEI numbers before provisioning devices. We must choose between:

1. In-process Luhn algorithm (pure computation)
2. External GSMA Device Check API (network call)
3. A local IMEI blocklist database (persistence dependency)

---

## Decision

**Use in-process Luhn algorithm (Option 1).**

---

## Rationale

| Criterion          | In-process Luhn | GSMA API | Local DB |
|--------------------|-----------------|----------|----------|
| Latency            | < 1 ms          | 50–200 ms | 2–5 ms  |
| External dependency| None            | Yes       | Yes      |
| Availability risk  | Zero            | Vendor uptime | DB uptime |
| Implementation cost| Low             | Medium    | High     |
| Accuracy           | Format only     | Full GSMA | Stale blocklist |

The requirement (FR-001, NFR-002) is **format validation** — confirming the IMEI is a well-formed, syntactically valid identifier. It does not require checking whether the device is stolen, blocked, or registered. In-process Luhn validation satisfies this requirement with zero additional infrastructure.

---

## Consequences

- **Positive:** No new runtime dependencies, no latency hit, no availability risk.
- **Positive:** The endpoint can be called at high volume without backpressure concerns.
- **Negative:** Does not detect stolen or non-existent IMEIs — out of scope for MVNO-002.
- **Future:** If blocklist checking is required, ADR-0004 should address it as a separate feature with a dedicated persistence tier.
