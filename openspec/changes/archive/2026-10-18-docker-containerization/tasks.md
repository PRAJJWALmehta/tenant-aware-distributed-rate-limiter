## 1. Upstream Mock & Verification Script

- [x] 1.1 Create `mock-upstream/server.py` using Python `http.server` to listen on port 8081, simulate 30ms latency, and return HTTP 200 with `{"choices": [{"text": "Mock LLM response"}]}`.
- [x] 1.2 Create `mock-upstream/Dockerfile` using `python:3-alpine` to run `server.py` and expose port 8081.
- [x] 1.3 Create executable `scripts/verify-docker.sh` to automate:
  - Gateway health check (`GET http://localhost:8080/health` -> 200 OK)
  - Valid tenant request (`POST http://localhost:8080/v1/chat/completions` with `X-Tenant-Id: test-tenant` -> 200 OK)
  - Redis token deduction inspection (checking key `rate_limit:test-tenant`)
  - Rate limit saturation assertion (exhausting bucket budget to trigger HTTP 429 Too Many Requests)

## 2. Gateway Dockerfile & Docker Compose Setup

- [x] 2.1 Create multi-stage `Dockerfile` for the gateway:
  - Stage 1: Build fat JAR with `maven:3.9.6-eclipse-temurin-21-alpine`
  - Stage 2: Runtime image with `eclipse-temurin:21-jre-alpine` exposing port 8080
- [x] 2.2 Create `docker-compose.yml` to orchestrate:
  - `redis`: Redis 7 Alpine on port 6379 with healthcheck (`redis-cli ping`)
  - `mock-upstream`: built from `./mock-upstream` on port 8081
  - `gateway`: built from `./` on port 8080 with environment variables `SPRING_DATA_REDIS_HOST=redis` and `UPSTREAM_URL=http://mock-upstream:8081`, depending on `redis` (condition: service_healthy) and `mock-upstream`

## 3. Container Build, Deployment & End-to-End Verification

- [x] 3.1 Build and launch containers in background: `docker compose up -d --build`.
- [x] 3.2 Verify container status and logs to ensure `redis`, `mock-upstream`, and `gateway` are running and healthy.
- [x] 3.3 Execute verification script: `bash scripts/verify-docker.sh`.
- [x] 3.4 Confirm all assertions pass:
  - Health check returns 200 OK
  - Tenant POST returns 200 OK
  - Redis token balance decrements
  - Burst traffic returns 429 Too Many Requests
- [x] 3.5 Stage files and commit:
  - `git add Dockerfile docker-compose.yml mock-upstream/ scripts/verify-docker.sh`
  - `git commit -m "feat: add multi-stage Dockerfile and docker-compose orchestration"`
