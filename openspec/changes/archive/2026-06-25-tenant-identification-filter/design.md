## Context

Our distributed rate limiter handles requests across multiple tenants. To correctly apply rate limiting per tenant, the gateway must accurately identify which tenant each request belongs to. Currently, we need a mechanism at the edge (Gateway Layer) to extract and validate tenant identifiers before they reach the core rate limiting logic.

## Goals / Non-Goals

**Goals:**
- Extract the `X-Tenant-Id` header from all incoming HTTP requests at the Gateway level.
- If the header is missing, extract the client IP address (checking `X-Forwarded-For` first, then falling back to the remote address) to use as the identifier.
- Inject the identified tenant ID (or IP address fallback) into the Reactor context so it's accessible downstream in the reactive pipeline.
- Provide a manual configuration toggle to disable the IP fallback mechanism, allowing administrators to shed unauthenticated traffic during a DDoS attack.

**Non-Goals:**
- Validating whether the tenant ID corresponds to an active or valid account (authentication/authorization beyond presence check).
- Implementing the actual rate limiting logic (this is handled by other components).

## Decisions

- **Use Spring WebFilter:** Since we are using a reactive Spring setup (likely Spring Cloud Gateway or WebFlux), a `WebFilter` is the idiomatic way to intercept and process all incoming requests globally before routing or specific handler invocation.
- **Reactor Context:** We will write the extracted `X-Tenant-Id` into the Reactor `Context`. This avoids modifying the request itself and allows downstream reactive services and components to pull the tenant ID directly from the context stream without needing to parse the headers again.
- **Header Name:** `X-Tenant-Id` will be used as the standard HTTP header for tenant identification.
- **IP Fallback Toggle:** If `X-Tenant-Id` is missing, we will fall back to the IP address (using `X-Forwarded-For` or raw connection). However, this behavior will be controlled by a configuration toggle (e.g., `ratelimiter.fallback.ip.enabled`). During a DDoS attack, administrators can disable this toggle to instantly return 401 Unauthorized for all requests missing the `X-Tenant-Id` header, dropping malicious traffic early.

## Risks / Trade-offs

- **Risk:** IP collisions for NAT'd networks or shared Wi-Fi if `X-Tenant-Id` is missing.
  - **Mitigation:** Accept that unauthenticated users sharing an IP will share a rate limit bucket. This is standard behavior for IP-based rate limiting.
- **Risk:** Performance overhead of intercepting every request.
  - **Mitigation:** A simple header check in a WebFilter is extremely lightweight and will not introduce noticeable latency.
