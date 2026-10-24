## Why

As the tenant-aware distributed rate limiter matures from core functional components (tenant identification, streaming token estimation, atomic Redis Lua token buckets, and reverse proxy routing) and passes high-concurrency performance benchmarks, clear architectural documentation and visual design artifacts are required. Production systems require comprehensive documentation of system components, end-to-end request lifecycles, and detailed engineering trade-offs (e.g. streaming Jackson parsing vs heap-buffered trees, atomic single Redis Lua scripts vs distributed lock coordination, reactive event-loop execution vs blocking thread pools) for engineering maintainability, architectural reviews, and technical deep-dives.

## What Changes

- Add visual Mermaid diagrams detailing system structure and execution flows in `docs/architecture/diagrams/`:
  - `request-lifecycle.mermaid`: Architectural component diagram showing client traffic through `TenantFilter` (extract `X-Tenant-Id` or IP fallback), `TokenEstimator` (Jackson streaming parser), `RateLimitService` (Lettuce reactive Redis Lua execution), and upstream LLM routing or HTTP 429 response handling.
  - `sequence-flows.mermaid`: Sequence diagrams illustrating all primary and edge-case execution paths:
    1. Happy path (HTTP 200 OK with token bucket deduction and upstream proxying).
    2. Budget exhausted path (HTTP 429 Too Many Requests with standard rate limit headers).
    3. Missing tenant identity path (HTTP 401 Unauthorized when IP fallback toggle is disabled).
    4. Redis failure / fallback path (graceful degradation fail-open routing).
- Add deep-dive architectural documentation in `docs/architecture/ARCHITECTURE.md`:
  - System overview and architectural goals.
  - Component breakdown and request lifecycle explanation.
  - SDE-2 architectural decisions and trade-off analysis:
    - *Memory Safety*: Streaming `JsonParser` ($O(1)$ memory) vs `ObjectMapper.readTree()` ($O(N)$ heap allocation).
    - *Atomicity & Throughput*: Single Redis Lua script vs Distributed Locks / Redlock (eliminating two-phase lock contention and network roundtrips).
    - *Reactive Pipeline*: Spring WebFlux / Reactor Netty event loops vs blocking Servlet threads (100k+ concurrent connections with minimal threads).
  - Failure modes, high-availability, and telemetry overhead guarantees.

## Capabilities

### New Capabilities
- `system-architecture-documentation`: Technical architecture documentation and visual Mermaid diagrams covering request lifecycles, sequence flows, and core SDE-2 engineering trade-offs.

### Modified Capabilities

## Impact

- New documentation and diagram artifacts: `docs/architecture/ARCHITECTURE.md`, `docs/architecture/diagrams/request-lifecycle.mermaid`, `docs/architecture/diagrams/sequence-flows.mermaid`.
- Codebase impact: Non-invasive documentation; no modifications to existing runtime code, configs, or tests.
- Tooling/Rendering: Diagrams adhere to standard Mermaid syntax for markdown viewer compatibility and GitHub rendering.
