# Non-Functional Requirements — device-details-service (MVNO-001)

**Ticket:** MVNO-001
**Stage:** 1 — Requirements
**Version:** 1.0.0
**Status:** Draft

---

## NFR-01: Availability

The device-details-service SHALL maintain a minimum availability of **99.9%** (three nines) measured on a rolling 30-day window, excluding scheduled maintenance windows communicated at least 48 hours in advance.

- Unplanned downtime MUST NOT exceed 43.8 minutes per 30-day period.
- The service MUST support zero-downtime rolling deployments in a Kubernetes environment.
- The service MUST be stateless to enable multiple replicas and restart without data loss.
- Readiness and liveness probes MUST be configured to prevent traffic routing to unhealthy instances (see FR-10).
- Dependency failures (vendor backend unavailability) MUST NOT cause service-level downtime; the circuit breaker and error responses serve callers while the vendor is degraded.

---

## NFR-02: Latency

The service SHALL meet the following end-to-end response time targets measured from request receipt to response transmission at the service boundary:

| Percentile | Target    | Measurement Condition                              |
|------------|-----------|----------------------------------------------------|
| p50        | ≤ 200 ms  | Steady-state load, vendor backend healthy          |
| p95        | ≤ 500 ms  | Steady-state load, vendor backend healthy          |
| p99        | ≤ 1,000 ms| Steady-state load, vendor backend healthy          |
| p99.9      | ≤ 2,000 ms| Including single retry attempt on transient failure|

- Latency targets apply exclusive of client network round-trip time.
- Timeout configurations for vendor calls MUST be set such that the p99 target is achievable even with one retry.
- Latency MUST be measured and reported via metrics (see NFR-07).

---

## NFR-03: Throughput and Capacity

The service SHALL be capable of handling the following request volumes without degradation below the latency targets in NFR-02:

- **Sustained throughput:** ≥ 500 requests per second (RPS) per service instance.
- **Peak throughput:** ≥ 1,000 RPS per service instance for burst periods up to 60 seconds.
- **Concurrent connections:** The service MUST support at least 1,000 concurrent HTTP connections per instance.
- Throughput targets MUST be validated via load testing in a pre-production environment prior to any production release.

---

## NFR-04: Scalability

The service SHALL be designed and deployed to scale horizontally in a Kubernetes environment.

- The service MUST be stateless — no in-process session state, no sticky sessions required.
- Horizontal Pod Autoscaling (HPA) MUST be supported based on CPU utilization and/or custom RPS metrics.
- Minimum production replica count: **2** (for fault tolerance).
- The service MUST scale from 2 to at least 20 replicas without architectural changes.
- All configuration, credentials, and feature flags MUST be externalized (environment variables or mounted ConfigMaps/Secrets) to allow uniform scaling.
- Database or shared state (if any cache is introduced) MUST support concurrent access from multiple replicas.

---

## NFR-05: Security

The service SHALL comply with the following security requirements:

### Authentication and Authorization
- Every API endpoint (except health probes) MUST require authentication via API Key or OAuth2 Client Credentials (see FR-11).
- Tenant isolation MUST be enforced at the service layer for every request (see FR-02).
- Authorization logic MUST be centralized and not duplicated across controllers or handlers.

### Transport Security
- All inbound and outbound HTTP communication MUST use TLS 1.2 or higher.
- Plain HTTP MUST NOT be accepted on any production-facing interface.
- TLS certificates MUST be rotated before expiry; certificate expiry monitoring MUST be in place.

### Credential Management
- API keys, OAuth2 client secrets, and vendor backend credentials MUST be stored in an external secrets management system (e.g., Kubernetes Secrets, Vault) and MUST NOT be hardcoded or committed to source control.
- Secrets MUST be injected at runtime via environment variables or mounted volumes.
- Credential rotation MUST be possible without service restart where feasible.

### Input Security
- All inbound request parameters MUST be validated and sanitized before use (see FR-08).
- The service MUST NOT construct dynamic queries or commands from unvalidated input.
- HTTP response headers MUST include appropriate security headers (e.g., `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security`).

### Data Protection
- MSISDN and IMSI values MUST be masked in all logs (last 4 digits visible; all preceding digits replaced with `*`).
- IMEI values MUST be masked in all logs.
- No PII or sensitive subscriber data MUST appear in metric label values, health check responses, or distributed traces.
- Data returned in API responses MUST be limited strictly to the canonical fields defined in FR-04; no vendor-internal or system-internal metadata MUST be leaked.

### Dependency Security
- All third-party library dependencies MUST be scanned for known vulnerabilities (CVE) as part of the CI/CD pipeline.
- High or critical CVEs in runtime dependencies MUST block release until remediated.

---

## NFR-06: Resilience and Fault Tolerance

The service SHALL remain operational and return well-formed error responses under the following failure conditions:

- Vendor backend complete unavailability (circuit breaker open state).
- Vendor backend intermittent latency spikes (handled by timeout and retry).
- Individual instance failure (handled by Kubernetes pod restart and HPA).
- Partial network partition between service and vendor backend.
- Invalid or malformed vendor backend responses.

Resilience configuration parameters (timeouts, retry counts, circuit breaker thresholds) MUST be externalized and tunable without code deployment.

The service MUST NOT enter an unrecoverable state due to downstream dependency failure. Bulkhead patterns SHOULD be applied to isolate vendor backend call thread pools from the main request thread pool.

---

## NFR-07: Observability — Metrics

The service SHALL expose application and business metrics in Prometheus-compatible format via `GET /actuator/prometheus`.

Metrics MUST include, at minimum:

| Metric Name                          | Type      | Description                                           |
|--------------------------------------|-----------|-------------------------------------------------------|
| `http_server_requests_seconds`       | Histogram | Inbound HTTP request latency by status, method, URI   |
| `vendor_backend_requests_seconds`    | Histogram | Vendor API call latency by outcome (success/error)    |
| `vendor_backend_errors_total`        | Counter   | Total vendor call failures by error type              |
| `circuit_breaker_state`              | Gauge     | Current circuit breaker state (0=closed, 1=open)      |
| `tenant_requests_total`              | Counter   | Total requests per MVNO tenant                        |
| `cache_hit_total`                    | Counter   | Cache hits (if caching enabled)                       |
| `cache_miss_total`                   | Counter   | Cache misses (if caching enabled)                     |
| `auth_failures_total`                | Counter   | Authentication/authorization failures by failure type |

- Metric label cardinality MUST be controlled — MSISDN MUST NOT be used as a label value.
- Dashboards and alerting rules SHOULD be defined alongside the service (e.g., as Grafana dashboard JSON and Prometheus alerting YAML).

---

## NFR-08: Observability — Distributed Tracing

The service SHALL support distributed tracing compatible with OpenTelemetry standards.

- Every inbound request MUST generate a trace span with the correlation ID as the trace ID or linked span.
- Outbound vendor backend calls MUST be instrumented as child spans within the same trace.
- Trace context MUST be propagated in W3C TraceContext format (`traceparent` / `tracestate` headers).
- Traces MUST be exportable to a configurable tracing backend (e.g., Jaeger, Zipkin, OTLP-compatible collector).
- Sensitive data (MSISDN, IMEI, IMSI) MUST NOT appear as span attribute values in traces.

---

## NFR-09: Observability — Logging

The service SHALL produce structured (JSON) application logs compliant with the following requirements:

- Log format MUST be JSON with consistent field names across all log entries.
- Every log entry MUST include: `timestamp` (ISO 8601 UTC), `level`, `correlationId`, `service`, `message`.
- Log level MUST be configurable at runtime without restart (via Spring Boot Actuator loggers endpoint or equivalent).
- Default log level in production: `INFO`. `DEBUG` MUST be disabled by default in production.
- Application logs and audit logs (FR-09) MUST be written to separate output streams.
- Logs MUST be written to `stdout`/`stderr` for collection by the container runtime log driver.
- PII masking rules (NFR-05) MUST be applied uniformly across all log outputs.

---

## NFR-10: Compliance and Data Governance

The service SHALL comply with the following data governance requirements:

- Subscriber data (MSISDN, IMEI, IMSI, IMSI) MUST be handled as Personally Identifiable Information (PII) under applicable data protection regulations (e.g., GDPR, CCPA — jurisdiction-specific requirements to be confirmed).
- The service MUST NOT persist subscriber data beyond the scope of a single request unless caching is explicitly enabled and configured (see FR-13).
- Audit log retention period MUST be configurable and governed by the organization's data retention policy.
- The service MUST NOT transmit subscriber data to any system other than the configured vendor backend and the requesting MVNO partner.
- Data lineage: all data flows (inbound request → vendor call → response) MUST be traceable via audit logs and distributed traces.

---

## NFR-11: Deployment and Infrastructure

The service SHALL be ready for containerized deployment in a Kubernetes environment, meeting the following requirements:

### Containerization
- A production-grade `Dockerfile` MUST be provided using a minimal, non-root base image (e.g., `eclipse-temurin:17-jre-alpine` or distroless equivalent).
- The container MUST run as a non-root user.
- Container image MUST be scannable for CVEs and MUST pass a configurable CVE threshold gate in CI/CD.

### Kubernetes Compatibility
- Kubernetes manifests (or Helm chart) MUST be provided covering: `Deployment`, `Service`, `HorizontalPodAutoscaler`, `ConfigMap`, `Secret` (placeholder), `PodDisruptionBudget`.
- Resource requests and limits MUST be defined for CPU and memory on all containers.
- Liveness and readiness probes MUST be configured (see FR-10).
- `PodDisruptionBudget` MUST ensure at least one replica is available during node drain operations.

### Configuration Management
- All environment-specific configuration MUST be externalized via environment variables or mounted Kubernetes ConfigMaps/Secrets.
- The service MUST start successfully with a minimal required set of environment variables and fail fast with a descriptive error if required configuration is missing.
- No environment-specific values MUST be hardcoded in application source code or build artifacts.

### Build and CI/CD
- The service MUST be buildable with a single command (e.g., `./mvnw clean package`).
- The CI/CD pipeline MUST run: unit tests, integration tests, static analysis, dependency vulnerability scan, Docker image build, and image push.
- Build artifacts MUST be reproducible — the same source commit MUST produce the same JAR and container image.

---

## NFR-12: Maintainability and Code Quality

- Code coverage MUST be maintained at a minimum of **80%** line coverage for unit tests and **70%** for integration tests across all modules.
- Static analysis (e.g., SpotBugs, Checkstyle, PMD) MUST be integrated into the build pipeline and MUST NOT report any high-severity violations in production code.
- The service MUST expose a Swagger/OpenAPI 3.0 specification at `/swagger-ui.html` and `/v3/api-docs` in non-production profiles.
- API versioning MUST be applied via URI path prefix (e.g., `/api/v1/...`) to allow future non-breaking evolution.
- All public service interfaces MUST be defined as Java interfaces to support testability and mock-based testing.
