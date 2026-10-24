## 1. Visual Design Diagrams

- [x] 1.1 Create `docs/architecture/diagrams/request-lifecycle.mermaid` mapping component and request lifecycles: Client -> `TenantFilter` (extract `X-Tenant-Id` / IP fallback) -> `TokenEstimator` (Jackson streaming parser) -> `RateLimitService` (Lettuce reactive Redis Lua) -> Upstream LLM Router / 429 Handler.
- [x] 1.2 Create `docs/architecture/diagrams/sequence-flows.mermaid` containing 4 discrete production sequence flows:
  - Sequence 1: Happy path (200 OK + token deduction)
  - Sequence 2: Budget exhausted (429 Too Many Requests)
  - Sequence 3: Missing tenant header with IP fallback disabled (401 Unauthorized)
  - Sequence 4: Redis connection failure (Fail-open path)

## 2. Technical Architecture Deep-Dive Documentation

- [x] 2.1 Create comprehensive `docs/architecture/ARCHITECTURE.md` detailing system architecture, component responsibilities, and end-to-end request processing flow.
- [x] 2.2 Document SDE-2 engineering trade-offs in `docs/architecture/ARCHITECTURE.md`:
  - *Memory Safety*: Streaming `JsonParser` vs `ObjectMapper.readTree()` ($O(1)$ memory vs $O(N)$ heap allocation under large concurrent prompt payloads).
  - *Atomicity*: Single Redis Lua script vs Distributed Locks / Redlock (eliminating two-phase lock contention and network roundtrips).
  - *Reactive Pipeline*: Spring WebFlux / Reactor Netty event loops vs blocking Servlet threads (100k+ concurrent connections with minimal threads).
- [x] 2.3 Embed rendered Mermaid diagrams into `docs/architecture/ARCHITECTURE.md` for seamless GitHub viewing.

## 3. Verification & Commit

- [x] 3.1 Verify Mermaid diagram syntax and markdown formatting compatibility.
- [x] 3.2 Commit architecture artifacts:
  - `git add docs/architecture/`
  - `git commit -m "docs: add system architecture diagrams and deep dive documentation"`
