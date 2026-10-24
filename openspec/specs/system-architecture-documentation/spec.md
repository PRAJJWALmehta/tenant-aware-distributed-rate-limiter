# system-architecture-documentation Specification

## Purpose
Establish technical architecture documentation and visual design artifacts detailing request lifecycles, sequence flows, core engineering decisions, and SDE-2 trade-offs (memory safety with streaming JSON parsing, atomicity with Redis Lua scripting, and reactive WebFlux pipelines).

## Requirements

### Requirement: Visual request lifecycle architectural diagram
The system SHALL provide a dedicated visual component and request lifecycle diagram in `docs/architecture/diagrams/request-lifecycle.mermaid`. The diagram SHALL depict incoming client requests flowing through `TenantFilter` (tenant extraction with IP fallback), `TokenEstimator` (streaming Jackson token cost calculation), `RateLimitService` (Lettuce reactive Redis Lua execution), and branching to either upstream proxying or HTTP 429 rate limit error responses.

#### Scenario: Visual representation of core pipeline components
- **WHEN** the `request-lifecycle.mermaid` diagram is rendered
- **THEN** it depicts the exact sequence of filters and services matching the production Spring WebFlux pipeline, including client entry, tenant identification, streaming token calculation, atomic Redis token bucket evaluation, upstream routing, and downstream response handling.

### Requirement: Visual sequence flow diagrams for request paths
The system SHALL provide comprehensive sequence diagrams in `docs/architecture/diagrams/sequence-flows.mermaid` modeling all primary execution paths and edge cases encountered in production.

#### Scenario: Happy path sequence flow
- **WHEN** a client submits a valid request with an identified tenant and sufficient token budget
- **THEN** the sequence diagram illustrates token estimation, atomic Redis bucket deduction returning `allowed=1`, proxying to the upstream LLM service, and returning HTTP 200 OK with rate limit and gateway telemetry headers.

#### Scenario: Budget exhausted sequence flow
- **WHEN** a client submits a request that exceeds the tenant's remaining token bucket quota
- **THEN** the sequence diagram illustrates token estimation, atomic Redis evaluation returning `allowed=0`, immediate termination of upstream proxying, and returning HTTP 429 Too Many Requests with retry headers.

#### Scenario: Missing tenant identity sequence flow
- **WHEN** a request arrives without `X-Tenant-Id` and IP fallback is disabled (`ratelimiter.fallback.ip.enabled=false`)
- **THEN** the sequence diagram illustrates rejection at `TenantFilter` with HTTP 401 Unauthorized without invoking `TokenEstimator` or Redis.

#### Scenario: Redis failure handling sequence flow
- **WHEN** Redis is unreachable or returns a connection timeout during rate limit evaluation
- **THEN** the sequence diagram illustrates reactive error handling where the gateway logs the failure and enforces a Fail-Closed response returning HTTP 503 Service Unavailable ("Rate limiter service unavailable") to prevent unbudgeted upstream spend, while documenting configurable Fail-Open alternatives.

### Requirement: Comprehensive architectural deep-dive documentation
The system SHALL provide a technical architecture document in `docs/architecture/ARCHITECTURE.md` analyzing the system design, reactive threading model, state management, and SDE-2 engineering trade-offs.

#### Scenario: Memory safety and streaming JSON parsing analysis
- **WHEN** evaluating JSON token estimation architecture in `ARCHITECTURE.md`
- **THEN** the document details the trade-off of streaming `JsonParser` ($O(1)$ memory, zero DOM tree allocation) versus `ObjectMapper.readTree()` ($O(N)$ heap allocation) under multi-gigabyte aggregate concurrent loads.

#### Scenario: Atomicity and Redis Lua scripting analysis
- **WHEN** evaluating distributed state and token bucket concurrency in `ARCHITECTURE.md`
- **THEN** the document contrasts single atomic Redis Lua execution against Distributed Locking / Redlock, highlighting the elimination of two-phase lock contention, lock leases, split-brain risks, and roundtrip network hops.

#### Scenario: Reactive WebFlux pipeline and event loop analysis
- **WHEN** evaluating connection scalability in `ARCHITECTURE.md`
- **THEN** the document contrasts Spring WebFlux / Reactor Netty non-blocking event loops against traditional blocking Servlet thread-per-request models for 100k+ concurrent long-lived LLM streams.
