## ADDED Requirements

### Requirement: Multi-stage Docker packaging for Gateway
The system SHALL provide a multi-stage `Dockerfile` for the rate-limiting gateway. Stage 1 SHALL use `maven:3.9.6-eclipse-temurin-21-alpine` to compile and package the Spring Boot application into an executable JAR. Stage 2 SHALL use `eclipse-temurin:21-jre-alpine` to create a lightweight runtime container exposing port 8080.

#### Scenario: Build produces executable slim image
- **WHEN** `docker build` is executed against the gateway `Dockerfile`
- **THEN** the image builds successfully without bundling Maven build dependencies or source code in the final runtime layer.

#### Scenario: Gateway container startup
- **WHEN** the gateway container runs with valid Redis connection environment variables
- **THEN** the Spring WebFlux application initializes and listens on port 8080.

### Requirement: Mock upstream LLM service
The system SHALL provide a standalone mock upstream service in `mock-upstream/` consisting of a Python HTTP server (`mock-upstream/server.py`) and container definition (`mock-upstream/Dockerfile`). The server SHALL listen on port 8081, simulate 30ms processing latency, and respond to POST requests with HTTP 200 and body `{"choices": [{"text": "Mock LLM response"}]}`.

#### Scenario: Mock upstream responds to completion requests
- **WHEN** a client sends a POST request to `http://localhost:8081/v1/chat/completions`
- **THEN** the mock server pauses for ~30ms and returns HTTP 200 with JSON payload `{"choices": [{"text": "Mock LLM response"}]}`.

#### Scenario: Mock upstream container build
- **WHEN** the mock upstream container is built via `mock-upstream/Dockerfile`
- **THEN** a minimal Python container starts and binds to port 8081.

### Requirement: Multi-container Compose orchestration
The system SHALL provide a `docker-compose.yml` file configuring and orchestrating three networked services:
1. `redis`: using `redis:7-alpine` exposed on port 6379, with an automated health check (`redis-cli ping`).
2. `mock-upstream`: built from `./mock-upstream` and exposed on port 8081.
3. `gateway`: built from `./` and exposed on port 8080, configured with environment variables `SPRING_DATA_REDIS_HOST=redis` and `UPSTREAM_URL=http://mock-upstream:8081`, depending on `redis` (condition `service_healthy`) and `mock-upstream`.

#### Scenario: Full stack launch
- **WHEN** `docker compose up -d --build` is executed
- **THEN** `redis`, `mock-upstream`, and `gateway` containers start up and report healthy/running status.

#### Scenario: Inter-container network communication
- **WHEN** `gateway` connects to Redis via hostname `redis` and upstream via hostname `mock-upstream`
- **THEN** network requests resolve and communicate across the shared container network.

### Requirement: Automated Docker verification suite
The system SHALL provide a script `scripts/verify-docker.sh` that automates end-to-end verification against the running Docker Compose environment. The script SHALL execute:
1. Health check verification (`GET http://localhost:8080/health` returning HTTP 200 "OK").
2. Authorized request verification (`POST http://localhost:8080/v1/chat/completions` with header `X-Tenant-Id: test-tenant` returning HTTP 200).
3. Redis token deduction inspection (verifying Redis key `rate_limit:test-tenant` has a decremented token balance).
4. Rate limit saturation check (firing repeated requests until bucket budget is exhausted and asserting HTTP 429 "Too Many Requests").

#### Scenario: Automated verification succeeds against running stack
- **WHEN** `bash scripts/verify-docker.sh` is executed while the compose stack is active
- **THEN** all verification steps pass with exit code 0 and output status confirmations.
