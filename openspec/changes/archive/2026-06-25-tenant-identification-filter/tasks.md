## 1. TenantIdentification Filter Implementation

- [x] 1.1 Write the failing tests: Test extracting `X-Tenant-Id`, `X-Forwarded-For` fallback, connection IP fallback, and 401 rejection when toggle is disabled (`src/test/java/com/ratelimiter/filter/TenantFilterTest.java`)
- [x] 1.2 Run tests to verify they fail
- [x] 1.3 Write minimal implementation: Extract `X-Tenant-Id` in a Spring WebFilter (`src/main/java/com/ratelimiter/filter/TenantFilter.java`). If missing, check `ratelimiter.fallback.ip.enabled` property. If true, extract from `X-Forwarded-For` or connection IP. If false, return 401. Add result to reactor context.
- [x] 1.4 Run tests to verify they pass
- [x] 1.5 Commit the changes
