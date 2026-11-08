## Context

The Tenant-Aware Distributed Rate Limiter is built as an edge API gateway reverse proxy in Java 21 and Spring WebFlux. It enforces token-aware rate limiting for multi-tenant LLM inference workloads using Redis. Over previous milestones (Tasks 1 through 7), the gateway implementation has evolved from raw routing and filter scaffolding into an end-to-end containerized system with verified empirical throughput exceeding 2,000 RPS and sub-2ms gateway overhead.

To support production onboarding, engineering maintenance, architectural reviews, and technical deep-dives (such as SDE-2 system design discussions), the architecture must be formally documented with visual artifacts. This change creates dedicated Mermaid diagrams for component lifecycles and sequence flows, and a deep-dive `ARCHITECTURE.md` analyzing key engineering trade-offs.

## Goals / Non-Goals

**Goals:**
- Provide clear visual Mermaid representations of the internal gateway pipeline in `docs/architecture/diagrams/request-lifecycle.mermaid`.
- Detail end-to-end sequence diagrams in `docs/architecture/diagrams/sequence-flows.mermaid` for happy path, budget exhaustion (429), unauthorized tenant (401), and Redis failure fail-open path.
- Provide a rigorous SDE-2 architectural document in `docs/architecture/ARCHITECTURE.md` articulating technical decisions and trade-offs:
  - Jackson streaming `JsonParser` ($O(1)$ memory allocation) vs DOM `readTree` ($O(N)$ heap bloat).
  - Atomic Redis Lua execution vs Distributed Lock / Redlock coordination.
  - Spring WebFlux / Reactor Netty event-loop pipelines vs blocking Servlet thread-per-request pools under 100k+ concurrent connections.
- Ensure all diagrams adhere to valid Mermaid syntax and render cleanly across GitHub markdown viewers.

**Non-Goals:**
- Modifying production runtime Java code, Redis Lua scripts, or test suites.
- Introducing new architectural components or altering the public HTTP contracts.

## Decisions

### Decision 1: Visual Artifact Representation via Standalone Mermaid Files and Markdown Embedding
- **Choice**: Maintain standalone `.mermaid` files in `docs/architecture/diagrams/` alongside full embedding in `docs/architecture/ARCHITECTURE.md`.
- **Rationale**: Standalone `.mermaid` files allow programmatic linting, external rendering pipelines (e.g. `mmdc` CLI or CI diagram checkers), and clean version control diffs, while embedding them directly inside `ARCHITECTURE.md` delivers an immediate, cohesive reading experience on GitHub.
- **Alternatives Considered**: Raw PNG/SVG exports (harder to diff in git, prone to becoming stale) or inline markdown diagrams only (loses standalone asset modularity).

### Decision 2: SDE-2 Architectural Trade-Off Categorization
- **Choice**: Structure `ARCHITECTURE.md` around three core systems engineering trade-offs: Memory Safety, State Atomicity, and Reactive Threading.
- **Rationale**: These three dimensions represent the primary architectural bottlenecks of high-concurrency LLM gateways:
  1. *Memory Safety*: Large request bodies (prompts) can trigger Garbage Collection pauses if fully buffered into DOM trees.
  2. *Atomicity*: Multi-instance gateways require strict token bucket consistency without distributed lock overhead.
  3. *Reactive Pipeline*: Slow upstream LLM inference generation locks traditional threads, making non-blocking event loops essential.
- **Alternatives Considered**: Generic module descriptions without trade-off analysis (fails to provide depth needed for architecture audits and technical reviews).

### Decision 3: Standardizing Sequence Diagram Scenarios
- **Choice**: Model four canonical production operational scenarios in `sequence-flows.mermaid`:
  1. Happy Path (200 OK + token deduction)
  2. Budget Exhausted (429 Too Many Requests)
  3. Tenant Header Missing with IP fallback disabled (401 Unauthorized)
  4. Redis Failure / Downstream Dependency Timeout (Fail-Open 200 OK with degraded telemetry)
- **Rationale**: These four scenarios encapsulate both nominal and critical failure modes, validating contract behavior across edge filter gates and resilience boundaries.

## Risks / Trade-offs

- **[Diagram Drift]** → As the codebase evolves, Mermaid diagrams may diverge from implementation. *Mitigation*: Diagrams explicitly model component names matching actual Spring WebFlux classes (`TenantFilter`, `TokenEstimator`, `RateLimitService`, `GatewayApplication`).
- **[Markdown Renderer Compatibility]** → Complex Mermaid syntax (e.g., HTML entities, special chars) can cause rendering failures on certain markdown engines. *Mitigation*: Use clean GitHub-flavored Mermaid syntax (`graph TD` and `sequenceDiagram`) verified against syntax linters.
