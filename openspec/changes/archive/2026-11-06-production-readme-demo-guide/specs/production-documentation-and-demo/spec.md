## ADDED Requirements

### Requirement: Automated Multi-Stage Terminal Demonstration Script
The repository SHALL provide an executable shell script at `scripts/demo.sh` that demonstrates all core gateway capabilities across 6 distinct stages with colorized ANSI output, automated assertions, and clean error handling.

#### Scenario: Stage 1 Health Check Verification
- **WHEN** `scripts/demo.sh` executes Stage 1
- **THEN** it sends `GET /health` to the gateway and asserts that the response status is HTTP 200 OK with body `OK`

#### Scenario: Stage 2 Normal Request with Token Estimation
- **WHEN** `scripts/demo.sh` executes Stage 2 with a valid payload (`POST /v1/chat/completions` with tenant ID)
- **THEN** it asserts that the gateway returns HTTP 200 OK and includes the `X-Gateway-Overhead-Ms` latency header

#### Scenario: Stage 3 Token Bucket Decrement Inspection
- **WHEN** `scripts/demo.sh` executes Stage 3 after token consumption
- **THEN** it queries Redis for the tenant's bucket key (`rate_limit:<tenant>`) and asserts that the token count has been decremented below capacity

#### Scenario: Stage 4 Burst Rate Limit Exhaustion
- **WHEN** `scripts/demo.sh` executes Stage 4 sending a burst exceeding remaining bucket capacity
- **THEN** it asserts that the gateway rejects excess requests with HTTP 429 Too Many Requests

#### Scenario: Stage 5 Tenant Identification and DDoS IP Fallback
- **WHEN** `scripts/demo.sh` executes Stage 5 testing requests without `X-Tenant-Id` header
- **THEN** it verifies tenant identification fallback behavior and demonstrates unauthorized rejection (HTTP 401) when IP fallback is disabled

#### Scenario: Stage 6 Graceful Degradation and Gateway Resilience
- **WHEN** `scripts/demo.sh` executes Stage 6
- **THEN** it validates or demonstrates the gateway's defensive failure behavior (fail-closed HTTP 503 Service Unavailable when downstream Redis is unreachable) and displays resilience trade-off guarantees

---

### Requirement: Flagship Repository Root README
The repository SHALL provide a comprehensive, production-grade `README.md` at the project root documenting system architecture, design rationale, benchmark metrics, quickstart instructions, and configuration options.

#### Scenario: Core Project Pitch and Problem Statement
- **WHEN** a reader reviews the introductory sections of `README.md`
- **THEN** it SHALL articulate the project purpose with badges and explain why traditional request-based rate limiting fails for LLM token workloads

#### Scenario: Embedded Architecture Diagram
- **WHEN** a reader views the architecture section of `README.md`
- **THEN** it SHALL present a valid embedded Mermaid diagram illustrating the request lifecycle from client to filter, Jackson tokenizer, Redis Lua script, and upstream forwarding

#### Scenario: Verified Benchmark Performance Summary
- **WHEN** a reader inspects the performance section of `README.md`
- **THEN** it SHALL document verified empirical metrics from load testing including 2,002.31 sustained RPS, 0.40ms p50 overhead, 2.24ms p95 overhead, and 100% burst enforcement precision

#### Scenario: Quickstart and Configuration Reference
- **WHEN** a developer follows the quickstart guide in `README.md`
- **THEN** it SHALL provide ready-to-run `docker compose` startup commands, sample `curl` requests, and a comprehensive configuration parameter reference table

---

### Requirement: SDE-2 Interview Guide and Talking Points
The repository SHALL provide a structured technical interview cheat-sheet at `docs/interview-talking-points.md` designed for SDE-2 architecture discussions and live portfolio walkthroughs.

#### Scenario: Five-Minute Live Demo Walkthrough Script
- **WHEN** an engineer conducts a 5-minute Gate 1 live demo
- **THEN** `docs/interview-talking-points.md` SHALL provide a step-by-step timed script specifying exact terminal commands to run and talking cues for each stage

#### Scenario: Probing Technical Interview Questions and Model Answers
- **WHEN** an interviewer asks challenging systems design questions
- **THEN** `docs/interview-talking-points.md` SHALL supply model answers for 8 in-depth questions covering Envoy/Kong comparisons, clock drift, token estimation error mitigation, noisy neighbors, resilience policies, streaming memory profiling, Lua atomicity, and multi-region scaling

#### Scenario: Quantified Resume Bullet Points
- **WHEN** updating an engineering resume or portfolio
- **THEN** `docs/interview-talking-points.md` SHALL provide ready-to-use quantified impact bullet points highlighting throughput, latency, and isolation guarantees
