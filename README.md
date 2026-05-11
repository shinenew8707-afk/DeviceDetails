# Device Details Service

MVNO Device Details microservice — retrieves device information for a subscriber MSISDN, enforces tenant isolation, and proxies requests to a vendor backend with circuit breaker and retry protection.

## Prerequisites

- Java 17
- Maven 3.9+
- Docker (optional)

## Quick Start

```bash
# Set required environment variables
export VENDOR_BASE_URL=http://your-vendor-host:9001
export OAUTH2_ISSUER_URI=http://your-oauth2-issuer

# Build and run
mvn spring-boot:run
```

The service starts on port 8080 by default.

## API Usage

### Get Device Details (API Key authentication)

```bash
curl -X GET "http://localhost:8080/api/v1/devices?msisdn=%2B14155551234&mvno=acme" \
  -H "X-API-Key: <your-api-key>" \
  -H "X-Correlation-Id: my-trace-id-123"
```

### Get Device Details (JWT authentication)

```bash
curl -X GET "http://localhost:8080/api/v1/devices?msisdn=%2B14155551234&mvno=acme" \
  -H "Authorization: Bearer <jwt-token>"
```

### Health and Readiness

```bash
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

### OpenAPI Docs

```
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

## Configuration Reference

| Environment Variable | Default | Description |
|---|---|---|
| `VENDOR_BASE_URL` | `http://localhost:9001` | Vendor backend base URL |
| `VENDOR_AUTH_HEADER_NAME` | `X-Vendor-Api-Key` | Auth header name for vendor calls |
| `VENDOR_AUTH_HEADER_VALUE` | `changeme` | Auth header value for vendor calls |
| `VENDOR_CONNECT_TIMEOUT_MS` | `3000` | Vendor connection timeout (ms) |
| `VENDOR_READ_TIMEOUT_MS` | `5000` | Vendor read timeout (ms) |
| `OAUTH2_ISSUER_URI` | `http://localhost:9000` | OAuth2 JWT issuer URI |
| `API_KEYS` | `{}` | Map of SHA-256(apiKey)→mvnoId, e.g. `{abc123hash: acme}` |
| `CACHE_ENABLED` | `false` | Enable Caffeine response cache |
| `CACHE_TTL_SECONDS` | `300` | Cache entry TTL in seconds |
| `CACHE_MAX_SIZE` | `10000` | Maximum number of cache entries |
| `RESILIENCE_TIMEOUT_DURATION` | `5s` | TimeLimiter timeout |
| `RESILIENCE_RETRY_MAX_ATTEMPTS` | `3` | Max retry attempts |
| `RESILIENCE_CB_SLIDING_WINDOW_SIZE` | `20` | Circuit breaker sliding window |
| `RESILIENCE_CB_FAILURE_RATE_THRESHOLD` | `50` | Circuit breaker failure rate % |
| `RESILIENCE_CB_WAIT_DURATION_OPEN` | `30s` | Circuit breaker open state wait |

## Docker

```bash
# Build image
docker build -t device-details-service:latest .

# Run container
docker run -p 8080:8080 \
  -e VENDOR_BASE_URL=http://vendor-host:9001 \
  -e OAUTH2_ISSUER_URI=http://issuer:9000 \
  device-details-service:latest
```

## Kubernetes

Deploy using the standard Spring Boot health probes (`/actuator/health/liveness` and `/actuator/health/readiness`) as liveness and readiness checks. The Prometheus metrics endpoint (`/actuator/prometheus`) is available for scraping by a Prometheus operator or ServiceMonitor.
