# ADR-0001: Resilience Strategy for Vendor Backend Integration

**Ticket:** MVNO-001
**Date:** 2026-05-11
**Authors:** Designer Agent (Stage 2)

## Status

Accepted

---

## Context

The device-details-service acts as a synchronous middleware between MVNO partners and a third-party device vendor backend. The vendor backend is an external system with no SLA guarantee visible to this service. Key risks include:

- The vendor backend may become temporarily unavailable (planned or unplanned downtime).
- The vendor backend may exhibit latency spikes that cascade into this service's response time degradation.
- Under sustained vendor failure, without a circuit breaker, every inbound MVNO request would block threads waiting for the vendor timeout, exhausting the thread pool and causing a full service outage.
- NFR-02 requires p99 ≤ 1,000ms and p99.9 ≤ 2,000ms even with one retry attempt.
- NFR-06 requires the service to remain operational and return well-formed error responses when the vendor is unavailable.

The resilience solution must be:
- Non-invasive to business logic (declarative or configuration-driven where possible).
- Fully externalized — no resilience parameters hardcoded in source.
- Observable — circuit breaker state and retry counts must be exposed as Prometheus metrics.

---

## Decision

**Adopt Resilience4j** as the resilience library, integrated via `resilience4j-spring-boot3` starter. Three patterns are applied in order for every vendor backend call:

### 1. Timeout (TimeLimiter)

Each vendor API call is wrapped in a `TimeLimiter` that aborts the call if it exceeds the configured duration. This prevents thread starvation under slow vendor responses.

### 2. Retry with Exponential Backoff

A `Retry` decorator wraps the vendor call (after timeout). On transient failures (network error, HTTP 5xx, timeout), the call is retried with exponential backoff and jitter:
- Base interval: 500ms
- Multiplier: 2×
- Max interval: 4,000ms
- Max attempts: 3 (1 initial + 2 retries)
- Retried exceptions: `ConnectException`, `ReadTimeoutException`, `VendorBackendException` (5xx)
- Not retried: `VendorNotFoundException` (404), `VendorAuthException` (401/403)

### 3. Circuit Breaker

A `CircuitBreaker` wraps the retry decorator. It tracks the failure rate over a sliding window. When the failure rate exceeds the threshold, the circuit opens and subsequent calls immediately throw `CallNotPermittedException`, bypassing the vendor entirely until the wait duration expires and a probe call is allowed.

### Decoration Order (outermost → innermost)

```
CircuitBreaker → Retry → TimeLimiter → VendorClient.call()
```

### Fallback

When `CallNotPermittedException` or `VendorUnavailableException` is caught, the service returns HTTP 503 with `errorCode: VENDOR_UNAVAILABLE`.

---

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Spring Retry | Less feature-complete; no built-in circuit breaker; lower observability integration with Micrometer |
| Hystrix (Netflix) | Officially deprecated since 2018; no Spring Boot 3.x support |
| Custom retry loop | Duplicates solved problems; not observable out-of-the-box; harder to configure externally |
| No resilience (accept timeouts) | Violates NFR-06 and NFR-02; thread starvation risk under vendor degradation |

---

## Consequences

**Positive:**
- Vendor failure is isolated; MVNO clients receive a fast, structured 503 rather than a hanging request.
- All parameters are externalized — SRE can tune without code deployment.
- Circuit breaker state, retry counts, and call durations are automatically exposed as Prometheus metrics via Micrometer integration.
- Retry with backoff reduces transient failure impact without overwhelming a recovering vendor.

**Negative:**
- With 3 retry attempts and exponential backoff, worst-case vendor call duration is ~500+1000+2000+5000ms(timeout) = ~8.5s, which would breach NFR-02 p99.9 if all retries time out. Timeout must be tuned (recommended: 2,000ms) so that 3 attempts fit within the 2,000ms p99.9 budget.
- Circuit breaker introduces a state machine that requires monitoring; a misconfigured threshold can cause premature circuit opening under acceptable transient failure rates.
- Adds a dependency on Resilience4j and Spring Cloud Circuit Breaker.

---

## Configuration Reference

All parameters are externalized via `application.yml` / environment variables.

| Parameter | Default | Environment Variable | Description |
|---|---|---|---|
| `timeout.duration` | `2s` | `RESILIENCE_TIMEOUT_DURATION` | Max wait time per vendor call attempt |
| `retry.maxAttempts` | `3` | `RESILIENCE_RETRY_MAX_ATTEMPTS` | Total attempts including initial call |
| `retry.waitDuration` | `500ms` | `RESILIENCE_RETRY_WAIT_DURATION` | Initial backoff interval |
| `retry.multiplier` | `2.0` | `RESILIENCE_RETRY_MULTIPLIER` | Exponential backoff multiplier |
| `retry.maxWaitDuration` | `4s` | `RESILIENCE_RETRY_MAX_WAIT_DURATION` | Maximum backoff interval |
| `circuitBreaker.slidingWindowSize` | `10` | `RESILIENCE_CB_SLIDING_WINDOW_SIZE` | Number of calls in sliding window |
| `circuitBreaker.failureRateThreshold` | `50` | `RESILIENCE_CB_FAILURE_RATE_THRESHOLD` | % failures to open circuit |
| `circuitBreaker.waitDurationInOpenState` | `30s` | `RESILIENCE_CB_WAIT_DURATION_OPEN` | Time circuit stays open before half-open probe |
| `circuitBreaker.permittedCallsInHalfOpenState` | `3` | `RESILIENCE_CB_HALF_OPEN_CALLS` | Probe calls in half-open state |
