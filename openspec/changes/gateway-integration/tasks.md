## 1. Integration Tests (Failing Tests)

- [x] 1.1 Create `src/test/java/com/ratelimiter/GatewayIntegrationTest.java` configured with Testcontainers Redis and `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`.
- [x] 1.2 Implement end-to-end integration test cases in `GatewayIntegrationTest`:
  - Valid POST request routing: verify HTTP 200 OK, route success response, and assert Redis hash field `tokens` in `rate_limit:<tenantId>` decreased by the estimated token count.
  - Rate-limit rejection: verify HTTP 429 Too Many Requests when requested tokens exceed remaining bucket capacity, and confirm no partial deduction occurred in Redis.
  - Malformed payload rejection: verify HTTP 400 Bad Request on invalid JSON body.
  - Unauthorized request rejection: verify HTTP 401 Unauthorized when `X-Tenant-Id` is missing and IP fallback is disabled.
  - Health endpoint verification: confirm GET `/health` continues returning HTTP 200 OK "OK" without requiring rate limiting or tenant headers.
- [x] 1.3 Run tests to verify they fail prior to gateway implementation: `mvn test -Dtest=GatewayIntegrationTest`.

## 2. Gateway Application Routing & Pipeline Implementation

- [x] 2.1 Modify `src/main/java/com/ratelimiter/GatewayApplication.java` to inject `RateLimitService` and `TokenEstimator` into a main WebFlux `RouterFunction<ServerResponse>`.
- [x] 2.2 Implement reactive pipeline flow for incoming POST API requests:
  - Extract tenant ID from Reactor Context (`TenantFilter.TENANT_KEY`).
  - Read request payload bytes via `request.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])`.
  - Calculate required tokens via `TokenEstimator.estimate()`, catching `IllegalArgumentException` to return HTTP 400 Bad Request.
  - Check and deduct tokens via `RateLimitService.isAllowed(tenantId, tokens)`.
  - Return HTTP 200 OK when allowed, or HTTP 429 Too Many Requests when rate-limited.
- [x] 2.3 Preserve existing `/health` route returning HTTP 200 OK "OK".

## 3. Verification & Commit

- [x] 3.1 Run integration tests to verify they pass: `mvn test -Dtest=GatewayIntegrationTest`.
- [x] 3.2 Run full test suite to ensure zero regressions across all components: `mvn test`.
- [x] 3.3 Commit changes to git.
