# Architecture Document — Device Details Service (MVNO-001)

**Version:** 1.0.0
**Stage:** 2 — Design Generated
**Spring Boot:** 3.2 | **Java:** 17

---

## Table of Contents

1. [High-Level Architecture (HLD)](#high-level-architecture)
2. [Low-Level Design (LLD)](#low-level-design)
3. [Technology Stack](#technology-stack)

---

## High-Level Architecture

### System Context Diagram

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              MVNO Partner Network                                │
│                                                                                  │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                         │
│   │  MVNO-A     │    │  MVNO-B     │    │  MVNO-N     │                         │
│   │  Client App │    │  Client App │    │  Client App │                         │
│   └──────┬──────┘    └──────┬──────┘    └──────┬──────┘                         │
│          │                  │                  │                                 │
└──────────┼──────────────────┼──────────────────┼─────────────────────────────────┘
           │ HTTPS            │ HTTPS            │ HTTPS
           │ (X-API-Key or    │ (X-API-Key or    │ (X-API-Key or
           │  Bearer JWT)     │  Bearer JWT)     │  Bearer JWT)
           ▼                  ▼                  ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                          API Gateway (External)                                  │
│              TLS termination, rate limiting, WAF, routing                        │
└───────────────────────────────────────┬──────────────────────────────────────────┘
                                        │ HTTP (internal)
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster (Production)                               │
│                                                                                  │
│   ┌───────────────────────────────────────────────────────────────────────────┐  │
│   │              device-details-service (2–20 replicas, HPA)                  │  │
│   │                                                                           │  │
│   │  ┌─────────────────────────────────────────────────────────────────────┐  │  │
│   │  │  CorrelationIdFilter → SecurityFilter → TenantIsolationFilter       │  │  │
│   │  │         → DeviceDetailsController → DeviceDetailsService            │  │  │
│   │  │         → VendorClient (WebClient + Resilience4j)                   │  │  │
│   │  │         → AuditLogService (async, dedicated appender)               │  │  │
│   │  └─────────────────────────────────────────────────────────────────────┘  │  │
│   └───────────────────────────────────────────────────────────────────────────┘  │
│                                                                                  │
└───────────┬──────────────────────────────────────┬───────────────────────────────┘
            │ HTTPS (mTLS optional)                │ Push metrics / traces
            ▼                                      ▼
┌─────────────────────────┐            ┌────────────────────────────┐
│  Third-Party Vendor     │            │  Observability Stack        │
│  Device Backend         │            │                             │
│  (External, per MVNO    │            │  Prometheus (metrics)       │
│   configuration)        │            │  Grafana (dashboards)       │
│                         │            │  Jaeger/Tempo (traces)      │
│  - Device lookup API    │            │  ELK / Loki (audit + app    │
│  - Returns IMEI, IMSI,  │            │    logs)                    │
│    VoLTE capability     │            │                             │
└─────────────────────────┘            └────────────────────────────┘

            ┌──────────────────────────────┐
            │  Auth Provider (External)    │
            │  OAuth2 / OIDC Identity      │
            │  Provider (per MVNO)         │
            │  - Issues JWT tokens         │
            │  - JWKS endpoint for         │
            │    signature verification    │
            └──────────────────────────────┘
```

### Component Overview

| Component | Type | Responsibility |
|---|---|---|
| MVNO Client App | External Consumer | Issues device lookup requests with credentials |
| API Gateway | External Infrastructure | TLS termination, WAF, rate limiting, routing |
| device-details-service | Core Microservice | Auth, tenant isolation, vendor proxy, audit logging |
| Vendor Device Backend | External Dependency | Canonical source of device/subscriber data |
| Auth Provider | External Dependency | OAuth2 JWKS endpoint; API Keys stored in service config |
| Prometheus | Observability | Scrapes /actuator/prometheus for metrics |
| OpenTelemetry Collector | Observability | Receives traces from OTEL SDK, forwards to backend |
| Log Aggregator (ELK/Loki) | Observability | Ingests JSON structured logs including audit stream |

### Deployment Topology

```
LoadBalancer Service (K8s)
        │
        ├── Pod 1: device-details-service (replica)
        ├── Pod 2: device-details-service (replica)
        ├── ...
        └── Pod N: device-details-service (HPA max 20)

HPA: CPU > 70% or RPS > 400 → scale up
PDB: minAvailable=1 (zero-downtime rolling deploy)

ConfigMap:  application config (non-sensitive)
Secret:     API key hashes, vendor credentials, OAuth2 client secrets
```

---

## Low-Level Design

### Package Structure

```
com.telecom.mvno.devicedetails
├── DeviceDetailsApplication.java           # Spring Boot entry point
│
├── api/
│   ├── DeviceDetailsController.java        # REST endpoint, validation, correlation
│   └── HealthController.java               # (actuator delegates; no custom needed)
│
├── service/
│   ├── DeviceDetailsService.java           # Orchestration: auth check, vendor call, mapping
│   └── TenantIsolationService.java         # MVNO identity enforcement
│
├── client/
│   └── VendorClient.java                   # WebClient + Resilience4j decorators
│
├── domain/
│   ├── request/
│   │   ├── DeviceDetailsRequest.java       # Validated inbound DTO
│   │   └── VendorDeviceRequest.java        # Outbound vendor request DTO
│   ├── response/
│   │   ├── DeviceDetailsResponse.java      # Canonical response DTO
│   │   ├── VendorDeviceResponse.java       # Raw vendor response DTO
│   │   └── ApiErrorResponse.java           # Error envelope DTO
│   └── audit/
│       └── AuditEntry.java                 # Audit log record DTO
│
├── config/
│   ├── WebClientConfig.java                # WebClient bean with connection pool, timeouts
│   ├── ResilienceConfig.java               # Resilience4j CircuitBreaker + Retry beans
│   ├── CacheConfig.java                    # Caffeine cache bean (conditional on property)
│   ├── ObservabilityConfig.java            # OpenTelemetry + Micrometer wiring
│   └── VendorProperties.java              # @ConfigurationProperties for vendor endpoints
│
├── exception/
│   ├── GlobalExceptionHandler.java         # @RestControllerAdvice, maps exceptions → error responses
│   ├── AuthenticationException.java        # 401 trigger
│   ├── AccessDeniedException.java          # 403 trigger
│   ├── SubscriberNotFoundException.java    # 404 trigger
│   ├── VendorUnavailableException.java     # 503 trigger
│   └── InvalidRequestException.java       # 400 trigger with violation list
│
├── security/
│   ├── SecurityConfig.java                 # Spring Security filter chain configuration
│   ├── ApiKeyAuthFilter.java               # Extracts + validates X-API-Key header
│   ├── ApiKeyAuthenticationProvider.java   # Resolves MVNO identity from API key
│   ├── JwtAuthenticationConverter.java     # Extracts MVNO claim from JWT
│   └── MvnoCredentialStore.java            # In-memory credential lookup (loaded from config)
│
├── audit/
│   ├── AuditLogService.java                # Builds + writes AuditEntry to dedicated appender
│   └── AuditLogFilter.java                 # Servlet filter; captures timing and outcome
│
├── filter/
│   └── CorrelationIdFilter.java            # MDC + response header X-Correlation-Id
│
└── mapper/
    └── ResponseMapper.java                 # VendorDeviceResponse → DeviceDetailsResponse
```

### Component Responsibilities

#### `DeviceDetailsController`
- Annotated `@RestController @RequestMapping("/api/v1/devices")`
- Accepts `GET` with `@RequestParam` `msisdn` and `mvno`
- Bean Validation: `@Pattern(regexp = "^\\+[1-9]\\d{6,14}$")` on msisdn, `@NotBlank` on mvno
- Injects `X-Correlation-Id` from MDC into response header via `HttpServletResponse`
- Delegates to `DeviceDetailsService`; returns `ResponseEntity<DeviceDetailsResponse>`
- No business logic

#### `DeviceDetailsService`
- Orchestrator — no HTTP, no security logic
- Calls `TenantIsolationService.assertIdentityMatch(authenticatedMvno, requestedMvno)`
- Calls `VendorClient.fetchDeviceDetails(mvno, msisdn)` with Resilience4j decorators applied
- Calls `ResponseMapper.map(vendorResponse, correlationId)` to produce canonical response
- Checks cache (if enabled) before vendor call; populates cache on success
- Throws domain exceptions; does not catch resilience exceptions — they propagate to `GlobalExceptionHandler`

#### `VendorClient`
- Spring `@Component` using injected `WebClient` bean
- Vendor base URL, path, and auth headers loaded from `VendorProperties` (per-MVNO config map)
- Resilience4j applied via annotations or programmatic decoration:
  - `@TimeLimiter` — default 5 s
  - `@Retry` — maxAttempts=3, exponential backoff 500ms base, 2x multiplier, 4s max
  - `@CircuitBreaker` — failureRateThreshold=50%, waitDurationInOpenState=30s, slidingWindowSize=20
- Returns `VendorDeviceResponse`; maps vendor 404 → `SubscriberNotFoundException`, 5xx/timeout → `VendorUnavailableException`
- Uses `.block()` to bridge reactive WebClient into MVC thread (acceptable pattern for proxy service)

#### `ResponseMapper`
- Stateless `@Component`
- Maps all fields from `VendorDeviceResponse` to `DeviceDetailsResponse`
- Handles null-safe mapping for all nullable vendor fields
- Injects `correlationId` from MDC

#### `TenantIsolationService`
- Extracts authenticated MVNO identity from `SecurityContextHolder`
- Compares against `mvno` request parameter (case-insensitive)
- Throws `AccessDeniedException` (403) on mismatch

#### `AuditLogService`
- Receives `AuditEntry` from `AuditLogFilter` (post-response)
- Masks msisdn: retains country code + last 4 digits, replaces middle digits with `****`
- Masks imei/imsi: not logged in audit stream
- Serializes `AuditEntry` to JSON via dedicated Logback `Logger` with separate `FileAppender` or `LokiAppender`
- Async write: uses `@Async` with a dedicated `ThreadPoolTaskExecutor` to avoid blocking request thread

#### `SecurityConfig`
- `@Configuration @EnableWebSecurity`
- Defines two `SecurityFilterChain` beans:
  - Actuator chain (order 1): `/actuator/**` — permit all, no auth
  - Main chain (order 2): `/api/**` — requires `ApiKeyAuthFilter` OR JWT `BearerTokenAuthenticationFilter`
- `ApiKeyAuthFilter` is registered before `UsernamePasswordAuthenticationFilter`
- JWT configured via `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))`
- Both auth paths populate `SecurityContext` with `Authentication` containing MVNO identity as principal

#### `ResilienceConfig`
- `@Configuration` defining named `CircuitBreakerConfig`, `RetryConfig`, `TimeLimiterConfig` beans
- All parameters externalized via `application.yaml` under `resilience4j.*`
- CircuitBreaker name: `vendorBackend`
- Retry name: `vendorBackend`
- TimeLimiter name: `vendorBackend`

### Threading Model

- **Main request thread pool**: Spring MVC on embedded Tomcat; default 200 threads, configurable via `server.tomcat.threads.max`
- **WebClient**: Non-blocking Netty event loop for vendor HTTP calls; bridged to MVC via `.block()` within a bounded `boundedElastic` scheduler if needed to avoid event loop starvation
- **Audit writes**: Dedicated `ThreadPoolTaskExecutor` (core=2, max=5, queue=500) — fire-and-forget
- **Java 17**: Standard thread model; virtual thread upgrade path available in Java 21

### Resilience4j Configuration Defaults

| Parameter | Default | Property Key |
|---|---|---|
| TimeLimiter timeout | 5s | `resilience4j.timelimiter.instances.vendorBackend.timeoutDuration` |
| Retry maxAttempts | 3 | `resilience4j.retry.instances.vendorBackend.maxAttempts` |
| Retry initial interval | 500ms | `resilience4j.retry.instances.vendorBackend.waitDuration` |
| Retry backoff multiplier | 2.0 | `resilience4j.retry.instances.vendorBackend.exponentialBackoffMultiplier` |
| Retry max interval | 4s | `resilience4j.retry.instances.vendorBackend.maxWaitDuration` |
| CB failureRateThreshold | 50% | `resilience4j.circuitbreaker.instances.vendorBackend.failureRateThreshold` |
| CB slidingWindowSize | 20 | `resilience4j.circuitbreaker.instances.vendorBackend.slidingWindowSize` |
| CB waitDurationInOpenState | 30s | `resilience4j.circuitbreaker.instances.vendorBackend.waitDurationInOpenState` |
| CB permittedCallsInHalfOpen | 3 | `resilience4j.circuitbreaker.instances.vendorBackend.permittedNumberOfCallsInHalfOpenState` |
| CB slowCallDurationThreshold | 3s | `resilience4j.circuitbreaker.instances.vendorBackend.slowCallDurationThreshold` |
| CB slowCallRateThreshold | 80% | `resilience4j.circuitbreaker.instances.vendorBackend.slowCallRateThreshold` |

All parameters are overridable per environment via Kubernetes ConfigMap.

### Cache Design (When Enabled)

- **Implementation**: Caffeine in-memory cache
- **Enabled by**: `device-details.cache.enabled=true` (default: `false`)
- **Cache key**: `String` composed as `"mvno:msisdn"` (e.g., `"MVNO-A:+12125551234"`)
- **Value type**: `DeviceDetailsResponse`
- **TTL**: configurable via `device-details.cache.ttl-seconds` (default: 300)
- **Max size**: configurable via `device-details.cache.max-size` (default: 10000)
- **Eviction**: Size-based LRU + time-based TTL
- **Cache miss**: Proceed to vendor call
- **Cache hit**: Return cached response; audit log marks `outcome=CACHE_HIT`
- **Invalidation**: TTL-only (no explicit invalidation); suitable for near-realtime device data

### Security Filter Chain Order

```
Incoming HTTP Request
        │
        ▼
[1] CorrelationIdFilter          (OncePerRequestFilter, order=1)
        │  - Read X-Correlation-Id header or generate UUID
        │  - Set MDC[correlationId]
        │  - Set response header X-Correlation-Id
        ▼
[2] ApiKeyAuthFilter             (OncePerRequestFilter, order=2, before UsernamePasswordAuthFilter)
        │  - If X-API-Key header present: validate hash, resolve MVNO, set SecurityContext
        │  - If absent: pass through (JWT filter handles next)
        ▼
[3] BearerTokenAuthenticationFilter  (Spring Security OAuth2 Resource Server)
        │  - If Authorization: Bearer ... present: validate JWT, extract mvno claim, set SecurityContext
        │  - If neither auth method succeeded: SecurityContext remains anonymous
        ▼
[4] Spring Security AuthorizationFilter
        │  - /actuator/** → permitAll
        │  - /api/**     → authenticated (rejects anonymous with 401)
        ▼
[5] DeviceDetailsController      (DispatcherServlet)
        │  - Invoke TenantIsolationService (403 check)
        │  - Invoke DeviceDetailsService
        ▼
[6] AuditLogFilter               (post-response, after response committed)
        │  - Capture response status, duration
        │  - Async write to AuditLogService
        ▼
Response returned to client
```

### Error Response Envelope Structure

```json
{
  "errorCode": "SUBSCRIBER_NOT_FOUND",
  "message": "No subscriber record found for the provided MSISDN",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "violations": [
    {
      "field": "msisdn",
      "rejectedValue": "+19995550000",
      "reason": "No active subscription found"
    }
  ]
}
```

- `violations` is `null` for non-validation errors (401, 403, 404, 503, 500)
- `violations` is a non-empty array for 400 validation errors
- `correlationId` always present (from MDC)

---

## Technology Stack

| Layer | Technology | Version / Notes |
|---|---|---|
| Language | Java | 17 (LTS) |
| Framework | Spring Boot | 3.2.x |
| Web | Spring Web MVC | Embedded Tomcat |
| Reactive HTTP Client | Spring WebFlux WebClient | Used only for vendor calls |
| Security | Spring Security | API Key filter + OAuth2 Resource Server |
| Resilience | Resilience4j | 2.x (via Spring Cloud) |
| Metrics | Micrometer + Prometheus | Spring Boot Actuator + micrometer-registry-prometheus |
| Tracing | OpenTelemetry (Micrometer Tracing) | micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp |
| Logging | Logback + Logstash Logback Encoder | JSON structured logs |
| Cache | Caffeine | Conditional; spring-boot-starter-cache |
| API Documentation | SpringDoc OpenAPI | springdoc-openapi-starter-webmvc-ui 2.x |
| Build | Maven | 3.9.x, multi-stage Docker build |
| Container | Docker | Non-root user, distroless or Eclipse Temurin slim |
| Orchestration | Kubernetes | Deployment, Service, HPA, ConfigMap, Secret, PDB |
| Config | Spring Config via ConfigMap/Secret | Environment variable injection |
| Validation | Jakarta Bean Validation (Hibernate Validator) | javax → jakarta namespace |
