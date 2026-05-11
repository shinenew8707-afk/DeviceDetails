# Code Review — device-details-service (MVNO-001)
**Iteration:** 1
**Reviewer:** Code Review Agent (Stage 4)
**Date:** 2026-05-11

## Summary
CONDITIONAL PASS — The service is structurally sound and covers all major functional requirements, but contains several security, correctness, and spec-alignment defects that must be resolved before the code can be approved for production.

---

## Findings

### Critical (must fix before merging)

| # | File | Line/Area | Finding | Required Fix |
|---|------|-----------|---------|--------------|
| C1 | `VendorClient.java` | `fetchDeviceDetailsAsync` — annotation order | Resilience4j annotations are ordered `@CircuitBreaker → @Retry → @TimeLimiter`, but the correct order for proxy-chained decoration is `@TimeLimiter` (innermost) → `@Retry` → `@CircuitBreaker` (outermost). In Spring AOP proxy decoration the last annotation listed is applied first (innermost), so the current order causes the circuit breaker to trip on timeout events before retry has a chance to run, and time limits are not applied per-attempt. | Reorder to `@TimeLimiter` → `@Retry` → `@CircuitBreaker` (top-to-bottom = outermost-to-innermost in Resilience4j Spring AOP). |
| C2 | `TenantIsolationService.java` | `assertIdentityMatch` — missing/null principal throws wrong exception | When authentication is `null` or principal is `null` the method throws `AccessDeniedException`, which maps to HTTP 403. FR-07 explicitly requires 401 for missing/invalid auth and 403 only for wrong-MVNO. The `GlobalExceptionHandler` correctly handles `AuthenticationException` as 401, but that path is never reached here. | Throw `AuthenticationException` (or a custom subclass) when the principal is absent; retain `AccessDeniedException` only for the MVNO mismatch branch. |
| C3 | `SecurityConfig.java` | `apiFilterChain` — `credentialStore` field not shown / potential NPE | `ApiKeyAuthFilter` is constructed with `credentialStore` but `SecurityConfig` does not declare or inject that field in the provided code. If this is a copy-omission the risk is low; if it is an actual missing injection the filter will throw `NullPointerException` at startup or at first request, completely bypassing API-key auth. | Verify `credentialStore` is `@Autowired`/constructor-injected into `SecurityConfig` and passed into the filter correctly. |
| C4 | `SecurityConfig.java` | `actuatorFilterChain` — actuator fully open | All `/actuator/**` endpoints are `permitAll` with no network-layer restriction. Sensitive actuator endpoints (`/actuator/env`, `/actuator/heapdump`, `/actuator/threaddump`) will be publicly accessible, exposing configuration and runtime PII. | At minimum restrict to `hasRole("ACTUATOR")` or an internal IP matcher; expose only `/actuator/health` and `/actuator/info` publicly. |

### High (must fix before production)

| # | File | Line/Area | Finding | Required Fix |
|---|------|-----------|---------|--------------|
| H1 | `GlobalExceptionHandler.java` | `handleMethodArgumentNotValid` — hardcoded error code | `MethodArgumentNotValidException` handler always returns `errorCode = "INVALID_MSISDN"` regardless of which field failed. A violation on the `mvno` parameter would incorrectly surface as `INVALID_MSISDN` instead of `INVALID_MVNO_NAME`, violating FR-08 and mismatching the OpenAPI spec. | Inspect the field errors to detect whether the failing field is `msisdn` or `mvno` (same logic already present in `handleConstraintViolation`) and set the error code accordingly. |
| H2 | `AuditLogFilter.java` | `resolveOutcome` — catch-all maps to `VENDOR_ERROR` | The `default`/catch-all branch of `resolveOutcome` returns `VENDOR_ERROR` for any status not explicitly listed, including HTTP 500. A 500 (internal server error) would be incorrectly classified as a vendor error in audit logs, corrupting operational analytics and potentially hiding application bugs. | Add an explicit `INTERNAL_ERROR` outcome (or equivalent) and map 5xx responses appropriately; reserve `VENDOR_ERROR` for 503 only. |
| H3 | `AuditLogFilter.java` | `doFilterInternal` — raw MSISDN read before auth completes | `msisdn` is captured from the query parameter at the start of the filter chain, before `ApiKeyAuthFilter` or the JWT resource server has validated the caller. For unauthenticated or adversarial requests the raw MSISDN is captured and passed to `auditLogService.log()`. Although `AuditLogService.maskMsisdn` does mask it before writing, the unmasked value is held in memory and passed across method boundaries as a plain `String` in the same request thread. If any exception or log statement in the audit path emits the `AuditEntry` before masking, PII leaks. Additionally, logging audit entries for completely unauthenticated requests may inflate audit volume with noise. | Capture the MSISDN after the filter chain completes (from the security context or a request attribute set by the controller) rather than from the raw query string pre-auth. At minimum document the risk. |
| H4 | `SecurityConfig.java` | `PasswordEncoder` — `NoOpPasswordEncoder` | `NoOpPasswordEncoder` is deprecated and stores/compares passwords in plain text. Even if API keys are hashed separately in `MvnoCredentialStore`, declaring this bean suggests other code paths may rely on it for plain-text comparison. | Remove or replace with `BCryptPasswordEncoder`; if it is unused, delete the bean entirely. |
| H5 | `ApiKeyAuthFilter.java` | Invalid API key silently passes through | When an `X-API-Key` header is present but resolves to no MVNO (`mvnoId.isEmpty()`), the filter calls `filterChain.doFilter` without setting a failed authentication, allowing the request to continue to the JWT resource server. A bad API key should result in an immediate 401, not silent passthrough that may succeed via a misconfigured fallback. | On `mvnoId.isEmpty()` when a key was presented, set a `401 Unauthorized` response and return; do not continue the chain. |

### Medium (should fix)

| # | File | Line/Area | Finding | Required Fix |
|---|------|-----------|---------|--------------|
| M1 | `DeviceDetailsService.java` | Manual cache management | Cache get/put logic is duplicated (two `cacheManager != null` / `cache != null` guard blocks) and implemented by hand rather than using Spring's `@Cacheable`. This pattern is error-prone and harder to maintain. | Refactor to use `@Cacheable(value = "deviceDetails", key = "#mvno + ':' + #msisdn")` unless nullable `CacheManager` injection specifically requires manual control. |
| M2 | `TenantIsolationService.java` | Information leakage in exception message | The `AccessDeniedException` message includes the authenticated MVNO value: `"Authenticated MVNO 'X' does not match requested MVNO 'Y'"`. The `GlobalExceptionHandler` echoes `ex.getMessage()` verbatim in the 403 response body, leaking internal tenant identity information to the caller. | Return a generic message such as `"Access denied"` in the exception (or in the handler); log the detail internally. |
| M3 | `VendorClient.java` | Blocking `.get()` on virtual-thread or reactive boundary | `fetchDeviceDetails` calls `CompletableFuture.get()` (blocking), wrapping an async WebClient call. If this service runs on a virtual-thread or Project Loom executor this is acceptable, but on a traditional thread pool it ties up a carrier thread for the full vendor round-trip. | Document the threading model or make the public API return `CompletableFuture`/`Mono` end-to-end. |
| M4 | `ResponseMapper.java` | MDC read inside domain mapper | `ResponseMapper.map` reads `MDC.get("correlationId")` directly, coupling infrastructure concerns (MDC / logging context) to domain mapping logic. If the mapper is invoked outside a request thread (e.g., async processing, tests) the correlation ID will be `null` silently. | Pass `correlationId` as an explicit parameter into `map()`; the controller already has it from `MDC.get()`. |
| M5 | `MvnoCredentialStore.java` | Unchecked `NoSuchAlgorithmException` | `MessageDigest.getInstance("SHA-256")` declares a checked `NoSuchAlgorithmException`. The provided code does not show exception handling; if it is swallowed or wrapped incorrectly, a JVM without SHA-256 (non-standard) would fail silently or throw an unchecked exception at auth time. | Explicitly catch and wrap in an `IllegalStateException` with a clear message; SHA-256 is guaranteed on all Java SE 8+ platforms so this is defensive housekeeping. |
| M6 | `AuditLogFilter.java` | `@Order(LOWEST_PRECEDENCE)` on audit filter | Ordering the audit filter last means it executes after all other filters in the response path. The `startTime` capture is correct, but if any earlier filter short-circuits the response (e.g., `CorrelationIdFilter` removes the MDC key in its `finally` block before audit runs), `MDC.get(MDC_CORRELATION_ID)` will return `null`. | Confirm filter ordering guarantees MDC is populated when audit runs, or read correlationId from the response header instead of MDC. |

### Low (nice to have)

| # | File | Line/Area | Finding | Required Fix |
|---|------|-----------|---------|--------------|
| L1 | `DeviceDetailsController.java` | Correlation ID set on response in controller | `CorrelationIdFilter` already sets `X-Correlation-Id` on every response. The controller redundantly reads from MDC and sets the same header again. This is harmless but noisy. | Remove the header-setting logic from the controller; rely solely on `CorrelationIdFilter`. |
| L2 | `JwtAuthenticationConverter.java` | `AuthenticationException` is abstract | `throw new AuthenticationException("...")` — `AuthenticationException` from Spring Security is abstract. This will fail to compile unless a concrete subclass (e.g., `BadCredentialsException`) is used. | Use a concrete subclass such as `BadCredentialsException` or define a custom exception class. |
| L3 | `GlobalExceptionHandler.java` | `correlationId()` method not shown | The handler references a `correlationId()` helper whose implementation is not provided. If it reads from MDC it is correct; if it generates a new UUID it will differ from the one already set by `CorrelationIdFilter`, producing mismatched IDs in the response body vs. the response header. | Ensure `correlationId()` reads from `MDC.get("correlationId")`, not from a new `UUID.randomUUID()`. |
| L4 | `AuditLogService.java` | `@Async` without explicit executor | `@Async` with no executor name uses the default Spring task executor, which is unbounded by default. Under high load audit tasks may queue indefinitely, consuming heap. | Configure a named, bounded `ThreadPoolTaskExecutor` for audit logging and reference it in `@Async("auditExecutor")`. |
| L5 | `DeviceDetailsController.java` | `mvno` parameter lacks format validation | `msisdn` is validated with `@Pattern`, but `mvno` only has `@NotBlank`. An adversarial caller can pass an arbitrarily long or specially crafted string. | Add `@Size(max = 64)` and a `@Pattern` matching expected MVNO name format. |

---

## Required Fixes
Numbered list ordered Critical → High, required before approval:

1. **(C1)** Correct Resilience4j annotation order on `VendorClient.fetchDeviceDetailsAsync` to `@TimeLimiter → @Retry → @CircuitBreaker`.
2. **(C2)** In `TenantIsolationService.assertIdentityMatch`, throw `AuthenticationException` (not `AccessDeniedException`) when the principal is absent, so the correct 401 is returned per FR-07.
3. **(C3)** Confirm `credentialStore` is properly injected into `SecurityConfig` and passed to `ApiKeyAuthFilter`; add the missing field/injection if absent.
4. **(C4)** Restrict `/actuator/**` endpoints — do not `permitAll`; expose only health/info publicly and require authentication or IP restriction for all other actuator endpoints.
5. **(H1)** Fix `handleMethodArgumentNotValid` in `GlobalExceptionHandler` to derive `errorCode` from the violating field name (`msisdn` → `INVALID_MSISDN`, `mvno` → `INVALID_MVNO_NAME`) instead of hardcoding `INVALID_MSISDN`.
6. **(H2)** Fix `AuditLogFilter.resolveOutcome` catch-all: map 5xx (other than 503) to an `INTERNAL_ERROR` outcome, not `VENDOR_ERROR`.
7. **(H3)** Refactor MSISDN capture in `AuditLogFilter` to occur after security processing, or add explicit documentation of PII handling risks for pre-auth capture.
8. **(H4)** Remove or replace `NoOpPasswordEncoder` bean in `SecurityConfig` with `BCryptPasswordEncoder`.
9. **(H5)** In `ApiKeyAuthFilter`, return `401 Unauthorized` immediately when a non-blank API key is presented but fails to resolve to an MVNO, rather than passing the request silently down the chain.

---

## Recommendations
- Consider migrating `DeviceDetailsService` cache management to `@Cacheable` to eliminate duplicated guard logic and reduce the risk of cache inconsistency (M1).
- Sanitize the `AccessDeniedException` message in `TenantIsolationService` to prevent tenant identity leakage in 403 response bodies (M2).
- Make the threading contract of `VendorClient` explicit — either go fully async end-to-end or document the blocking boundary and ensure the thread pool is sized accordingly (M3).
- Move the `correlationId` parameter into `ResponseMapper.map()` signature to decouple domain mapping from MDC/infrastructure context (M4).
- Add `@Size(max = N)` and a format `@Pattern` to the `mvno` request parameter in `DeviceDetailsController` to guard against oversized/malformed inputs (L5).
- Configure a dedicated bounded `ThreadPoolTaskExecutor` for `@Async` audit log writes to prevent unbounded queue growth under load (L4).
- Use a concrete Spring Security exception subclass (e.g., `BadCredentialsException`) in `JwtAuthenticationConverter` — `AuthenticationException` is abstract and will not compile (L2).
- Verify `GlobalExceptionHandler.correlationId()` reads from `MDC.get("correlationId")` to guarantee consistency with the `X-Correlation-Id` response header set by `CorrelationIdFilter` (L3).

---

## OpenAPI Spec Alignment

| Endpoint / Response | Status | Notes |
|---|---|---|
| `GET /api/v1/devices` route exists | ✅ Matched | Controller maps `GET /api/v1/devices` with `msisdn` and `mvno` query params. |
| Auth: ApiKey (`X-API-Key`) | ✅ Matched | `ApiKeyAuthFilter` handles this path. |
| Auth: BearerAuth (JWT) | ✅ Matched | `oauth2ResourceServer` JWT configured in `SecurityConfig`. |
| `200` DeviceDetailsResponse fields | ✅ Matched | All spec fields present: `msisdn`, `mvno`, `deviceName`, `make`, `model`, `hasVolte`, `imei`, `imsi`, `additionalAttributes`, `correlationId`. |
| `400` `INVALID_MSISDN` | ⚠️ Partial | Correct code returned for MSISDN violations; broken for MVNO violations (hardcoded to `INVALID_MSISDN` — see H1). |
| `400` `INVALID_MVNO_NAME` | ❌ Mismatched | `MethodArgumentNotValidException` handler never emits this code due to hardcoded `INVALID_MSISDN` (H1). `ConstraintViolationException` handler correctly differentiates, but controller uses `@RequestParam` validation which raises `ConstraintViolationException`, so in practice this may work — depends on validation trigger path. Needs explicit verification and the handler fix to be safe. |
| `401` `AUTHENTICATION_FAILED` | ⚠️ Partial | `JwtAuthenticationConverter` and `GlobalExceptionHandler` handle this correctly. However, missing-principal case in `TenantIsolationService` incorrectly returns 403 instead of 401 (C2). |
| `403` `ACCESS_DENIED` | ⚠️ Partial | Correct for MVNO mismatch. Incorrectly triggered for missing principal (should be 401 — C2). |
| `404` `SUBSCRIBER_NOT_FOUND` | ✅ Matched | `SubscriberNotFoundException` handler returns 404 with correct code. |
| `503` `VENDOR_UNAVAILABLE` + `Retry-After` | ✅ Matched | Handler sets `Retry-After: 30` header and returns correct code. |
| `500` `INTERNAL_ERROR` | ✅ Matched | Generic handler returns 500 with `INTERNAL_ERROR`; message is generic (no leakage). |
| `X-Correlation-Id` response header | ✅ Matched | `CorrelationIdFilter` sets header on all responses. Controller sets redundant header (L1). |
| Null fields not omitted (FR-04) | ✅ Matched | `ResponseMapper` maps `null` vendor fields as `null`; spec requires null not omitted — depends on `@JsonInclude` config (not shown; verify `NON_NULL` is not set globally). |

---

## Approval Recommendation

**APPROVE WITH REQUIRED FIXES**

The architecture is well-structured and covers the primary functional requirements. All nine required fixes (C1–C4, H1–H5) must be addressed and verified before merging to main or deploying to any environment. No finding constitutes a fundamental design flaw requiring a re-architecture; all are correctable in the existing implementation.
