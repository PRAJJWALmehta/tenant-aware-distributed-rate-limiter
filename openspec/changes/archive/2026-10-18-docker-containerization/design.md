## Context

The tenant-aware distributed rate limiter has completed its core functional implementation: tenant identification, streaming token cost estimation, Redis Lua token bucket rate limiting, and reactive WebFlux routing. However, until now, testing has relied on local unit and integration tests with ephemeral Testcontainers.

To enable standalone local execution, developer evaluation, and downstream performance benchmarking (k6), the system needs a reproducible multi-container topology orchestrating the Gateway, a distributed Redis cache, and a simulated upstream LLM provider.

## Goals / Non-Goals

**Goals:**
- Provide a multi-stage `Dockerfile` producing a lean, production-grade runtime container for the Spring Boot Gateway.
- Implement a mock upstream LLM service (`mock-upstream/server.py` and `mock-upstream/Dockerfile`) returning OpenAI-compatible completion responses with simulated 30ms latency.
- Orchestrate the three services (`gateway`, `redis`, and `mock-upstream`) via `docker-compose.yml` with proper service dependencies, environment variable overrides, and health checks.
- Create an automated end-to-end verification script (`scripts/verify-docker.sh`) validating health checks, authorized routing, Redis token balance decrement, and 429 rate limit saturation.

**Non-Goals:**
- Kubernetes manifests or Helm charts (Docker Compose is targeted for local development and benchmarking).
- Full OpenAI API parity in mock upstream (mock only needs to accept POST requests and return mock completion JSON).
- Production secrets management or TLS termination (handled by ingress/service mesh in cloud environments).

## Decisions

- **Multi-Stage Dockerfile for Spring Boot Gateway**:
  - *Decision*: Use `maven:3.9.6-eclipse-temurin-21-alpine` as the builder stage to compile the fat JAR and `eclipse-temurin:21-jre-alpine` as the final runtime image.
  - *Rationale*: Isolates build tools and source code from the final artifact, resulting in a minimal attack surface and small image size (~150MB vs >600MB).
  - *Alternative considered*: Building JAR on the host machine and copying `target/*.jar`. Rejected because it introduces host environment dependencies (requiring Maven/Java 21 installed on the developer's machine).

- **Standard Library Python Mock Upstream**:
  - *Decision*: Implement `mock-upstream/server.py` using Python's built-in `http.server.BaseHTTPRequestHandler` running on `python:3-alpine`.
  - *Rationale*: Zero external dependencies (no pip install, Flask, or FastAPI needed), fast image build (<5 seconds), and deterministic 30ms sleep mimicking LLM inference latency.
  - *Alternative considered*: WireMock or MockServer. Rejected because standard library Python is far lighter, starts instantly, and consumes negligible memory (<20MB RAM).

- **Docker Compose Topology & Service Dependency**:
  - *Decision*: Wire services over an explicit bridge network, using Redis health checks (`redis-cli ping`) and `depends_on: { redis: { condition: service_healthy } }` for the Gateway.
  - *Rationale*: Prevents Spring Boot from failing during startup if Redis takes a few seconds to initialize.
  - *Environment configuration*: Gateway consumes `SPRING_DATA_REDIS_HOST=redis` and `UPSTREAM_URL=http://mock-upstream:8081` via standard Spring Boot environment variable mappings.

- **Automated Verification Script (`scripts/verify-docker.sh`)**:
  - *Decision*: Write a bash script that queries endpoints using `curl` and queries Redis via `docker compose exec -T redis redis-cli`.
  - *Rationale*: Provides one-command testability with zero extra dependencies beyond bash, curl, and docker compose.

## Risks / Trade-offs

- **[Risk] Docker build cache invalidation on code changes** → Mitigation: Copy `pom.xml` first and run `mvn dependency:go-offline` before copying `src/` to optimize layer caching.
- **[Risk] Spring Boot Gateway starting before Redis is ready** → Mitigation: Configure Redis container health check with interval/retries and `condition: service_healthy` on `gateway`.
- **[Trade-off] Multi-container local resource usage** → Both mock-upstream and Redis use Alpine images (<30MB RAM each); Gateway JRE is tuned with container support flags.
