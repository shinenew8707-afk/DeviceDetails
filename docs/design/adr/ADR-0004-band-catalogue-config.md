# ADR-0004: MVNO Band Catalogue — Configuration vs Database

**Status:** Accepted
**Date:** 2026-05-15
**Ticket:** MVNO-003
**Deciders:** Platform Engineering

---

## Context

We need a catalogue that maps each MVNO tenant to its required network frequency bands. The options are:

1. **`application.yml` + `@ConfigurationProperties`** — bands defined in the Spring config file, reloaded via config map update + rolling restart
2. **Database table** — a new `mvno_band_requirements` table, read at runtime via JPA
3. **Hardcoded enum in Java** — bands encoded directly in source

---

## Decision

**Use `application.yml` + `@ConfigurationProperties` (Option 1).**

---

## Rationale

| Criterion               | Config file | Database | Hardcoded enum |
|-------------------------|-------------|----------|----------------|
| New infra required      | None        | Yes (DB migration, new table) | None |
| Runtime update          | Config-map rolling restart | Live update | Code + deploy |
| Type-safety             | Yes (bound record) | No | Yes |
| Ops complexity          | Low         | Medium   | Low |
| Suitable for change rate | Low-medium  | High     | Very low |

MVNO band configurations change infrequently (new MVNO onboarding or spectrum acquisition events). A config-map rolling restart is an acceptable operational model. Introducing a new database table adds migration complexity, a new JPA entity, and a runtime dependency on the DB for a low-change dataset — not justified for this feature.

Option 3 (hardcoded) is rejected because it requires a code change and redeployment for every MVNO update.

---

## Consequences

- **Positive:** Zero new infrastructure, type-safe binding, consistent with 12-factor config principle.
- **Positive:** Kubernetes config-map updates trigger rolling restarts automatically — update SLA matches current deployment cadence.
- **Negative:** Not suitable if band requirements need live, sub-minute updates without restart. If that becomes a requirement, migrate to a database or a remote config service (ADR-0005).
- **Future:** If the MVNO count grows beyond ~50 or update frequency increases, revisit with a dedicated config service.
