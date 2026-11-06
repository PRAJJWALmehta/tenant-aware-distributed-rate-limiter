## Context

The Tenant-Aware Distributed Rate Limiter project has completed all core functional and performance milestones: reactive Spring Boot WebFlux gateway with Netty, Jackson Streaming token estimation, distributed atomic Redis Lua token bucket algorithm, Docker Compose multi-container setup, and rigorous k6 concurrency benchmarks validating >2,000 sustained RPS with <2.3ms p95 gateway overhead. 

To transition this repository into a flagship production-ready portfolio project and prepare for SDE-2 technical interviews, we need three interconnected deliverables:
1. An automated, interactive 60-second terminal demonstration script (`scripts/demo.sh`) with colorized ANSI output showing all key runtime capabilities.
2. A comprehensive, visually striking root `README.md` that synthesizes the technical architecture, problem statement, performance metrics, and quickstart instructions.
3. A strategic interview cheat-sheet (`docs/interview-talking-points.md`) outlining a 5-minute live demo script, 8 rigorous interviewer probing questions with model answers, and quantified resume bullet points.

## Goals / Non-Goals

**Goals:**
- Provide a zero-friction, automated 60-second demonstration script (`scripts/demo.sh`) that verifies health, valid request processing, Redis token decrement, 429 burst exhaustion, DDoS IP fallback handling, and resilience/degradation states with clean colored terminal output.
- Deliver an executive-ready flagship `README.md` in the repository root covering the LLM rate limiting problem statement, embedded Mermaid architecture diagrams, tech stack rationale, benchmark results, and a configuration reference.
- Create `docs/interview-talking-points.md` featuring a 5-minute Gate 1 walkthrough script, 8 deep-dive SDE-2 architecture questions with definitive technical answers, and copy-paste resume bullets.
- Maintain documentation integrity and synchronization with existing architectural blueprints (`docs/architecture/ARCHITECTURE.md`) and benchmark data (`docs/BENCHMARKS.md`).

**Non-Goals:**
- Refactoring the core Java application code or altering Lua script semantics (the runtime already satisfies all architectural SLOs).
- Introducing new external infrastructure components (e.g., Consul, Prometheus, Grafana dashboards) outside the existing Docker Compose setup.
- Modifying previously archived OpenSpec change records.

## Decisions

### 1. Structure of `scripts/demo.sh`
- **Decision**: Implement a modular, robust bash script with ANSI color-coding (`GREEN`, `RED`, `YELLOW`, `BLUE`, `CYAN`), trap-based temporary file cleanup (`trap cleanup EXIT`), and prerequisite verification (Docker daemon, running containers, or local fallback).
- **Rationale**: Live interview demos and automated verifications must never fail due to dangling files or silent errors (`set -euo pipefail`). The script must guide the viewer step-by-step with clear explanations for each of the 6 stages.
- **Alternatives Considered**: 
  - *Python demo script*: Requires a Python virtual environment and external dependencies (`requests`), introducing friction. A pure POSIX/bash curl script runs natively on any developer workstation without setup overhead.

### 2. Multi-Stage Demo Coverage
- **Stage 1**: Health check verification (`GET /health` -> 200 OK).
- **Stage 2**: Normal request with token estimation (`POST /v1/chat/completions` -> 200 OK + `X-Gateway-Overhead-Ms` header validation).
- **Stage 3**: Redis token bucket decrement inspection (`HMGET rate_limit:<tenant> tokens last_refill_ts`).
- **Stage 4**: Burst exhaustion triggering `429 Too Many Requests` (saturating the 100-token capacity).
- **Stage 5**: Tenant identification & DDoS fallback toggle (verifying behavior with missing tenant header vs IP-based attribution).
- **Stage 6**: Gateway resilience & failure handling (verifying defensive 503 response when Redis/upstream is disrupted, or demonstrating fail-open/fail-closed trade-off analysis).

### 3. Flagship `README.md` Composition
- **Decision**: Position the project as an enterprise LLM infrastructure component rather than a generic web tutorial. Emphasize why request-based rate limiting fails for generative AI (token length variance, prompt vs completion asymmetry, compute exhaustion).
- **Rationale**: Hiring managers and senior interviewers look for domain awareness and system design depth. Presenting empirical benchmarks (2,002 RPS, 2.24ms p95 overhead) alongside architecture diagrams and clear setup instructions establishes immediate technical credibility.

### 4. SDE-2 Interview Guide Format
- **Decision**: Organize `docs/interview-talking-points.md` into three practical sections:
  1. A timed 5-minute script with explicit "What to show" and "What to say" cues.
  2. 8 challenging interviewer questions covering trade-offs (custom gateway vs Envoy/Kong, clock drift, estimation inaccuracy, noisy neighbors, resilience policies).
  3. Tailored resume bullet points emphasizing scale, latency reduction, and concurrency guarantees.

## Risks / Trade-offs

- **[Risk] Docker service state during demo**: `scripts/demo.sh` assumes the Docker Compose stack is running or starts it automatically.
  - *Mitigation*: The demo script checks service readiness first, prompts the user or auto-detects running containers, and prints troubleshooting guidance if containers are offline.
- **[Risk] Redis state pollution across demo stages**: Stage 3 and Stage 4 consume tenant tokens, which could cause subsequent tests to fail if the tenant bucket is drained.
  - *Mitigation*: Use distinct tenant identifiers for independent stages (e.g., `demo-nominal-tenant`, `demo-burst-tenant`) or flush the specific test tenant key in Redis before starting the stage.
- **[Risk] Discrepancies between docs and benchmark numbers**: Stale metrics in README could undermine confidence.
  - *Mitigation*: Exactly mirror the validated empirical figures from `docs/BENCHMARKS.md` (2,002.31 sustained RPS, 0.40ms p50, 2.24ms p95, 4.21ms p99 overhead).
