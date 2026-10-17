# Future Enhancements

> **Note:** Active implementation tasks for Phase 1 are tracked in [`docs/plan-tenant-rate-limiter.md`](file:///Users/prajjwalmehta/Development/Personal/tenant-aware-distributed-rate-limiter/docs/plan-tenant-rate-limiter.md). This document tracks long-term architectural enhancements and post-Gate 1 feature iterations.

---

### Architectural & Deployment Iterations
- [ ] **Sidecar Deployment Pattern:** Migrate the Standalone API Gateway to an Envoy/Istio sidecar proxy or custom service mesh filter to integrate natively alongside microservice pods without network hops.
- [ ] **Bearer Token Identity Extraction:** Extract tenant identity dynamically from the API key inside the `Authorization: Bearer` header, resolving tenant identities via cached Redis hash lookups rather than relying solely on the explicit `X-Tenant-Id` header.
- [ ] **Adaptive DDoS / Traffic Shedding Circuit Breaker:** Implement an automated governor to disable IP-fallback (`ratelimiter.fallback.ip.enabled: false`) when CPU throttling, severe network queueing, or unauthenticated traffic spikes are detected.
- [ ] **Prometheus & Micrometer Metrics:** Export real-time operational metrics for Grafana dashboards:
  - `rate_limit.tokens.deducted` (counter tagged by tenant)
  - `rate_limit.requests.rejected_429` (counter)
  - `redis.lua.latency.milliseconds` (timer / histogram)
  - `gateway.streaming.parser_overhead` (timer)

---

### Algorithmic & Rate Limiting Enhancements
- [ ] **Sliding Window Counter Hybrid:** Support bursty tenants by pairing the token bucket with a strict sliding window counter for sub-second spike protection.
- [ ] **Tiered Tenant Priority (QoS):** Allow high-paying enterprise tenants guaranteed minimum throughput allocation even under extreme global Redis contention.
- [ ] **Asynchronous True-Up Streaming Reconciliation:** For long-running streaming completions (`stream: true`), listen to upstream chunk terminations and refund/true-up the difference between estimated token usage and actual consumed tokens.
