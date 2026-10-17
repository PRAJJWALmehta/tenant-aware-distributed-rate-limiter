# gateway-integration Specification

## Purpose
Integrate tenant identification, payload token estimation, and distributed token bucket rate limiting into an end-to-end WebFlux routing pipeline for the API gateway.

## Requirements

### Requirement: End-to-end request rate limiting pipeline
The Gateway SHALL integrate tenant identification, payload token estimation, and distributed token bucket rate limiting into the reactive WebFlux routing pipeline. When a request arrives for an identified tenant and the tenant has sufficient tokens in their Redis bucket, the Gateway SHALL deduct the estimated tokens and route the request successfully with HTTP 200 OK.

#### Scenario: Successful request routing with token deduction
- **WHEN** an identified tenant sends a valid POST request requiring $N$ tokens and has at least $N$ tokens in their Redis bucket
- **THEN** the Gateway deducts $N$ tokens from the tenant's bucket in Redis and returns HTTP 200 OK.

#### Scenario: Route preserves existing health endpoint
- **WHEN** a client sends a GET request to `/health`
- **THEN** the Gateway bypasses tenant identification and rate limiting, returning HTTP 200 OK with body "OK".

### Requirement: Rate limit rejection
The Gateway SHALL reject incoming requests with HTTP 429 Too Many Requests when the requested tokens exceed the tenant's available token balance in Redis, preserving the remaining token balance without partial deduction.

#### Scenario: Request exceeds available bucket balance
- **WHEN** a tenant sends a POST request requiring more tokens than currently available in their Redis bucket
- **THEN** the Gateway responds with HTTP 429 Too Many Requests and does not deduct any tokens from the bucket.

### Requirement: Malformed payload handling
The Gateway SHALL reject requests with HTTP 400 Bad Request when the request body contains invalid or malformed JSON that cannot be parsed by the token estimator.

#### Scenario: Request contains invalid JSON
- **WHEN** a client sends a POST request with a malformed JSON payload
- **THEN** the Gateway responds with HTTP 400 Bad Request and does not invoke rate limit deduction.

### Requirement: Unauthorized request handling
The Gateway SHALL reject requests with HTTP 401 Unauthorized when tenant identification cannot be established (e.g., missing `X-Tenant-Id` header when IP fallback is disabled).

#### Scenario: Missing tenant header with IP fallback disabled
- **WHEN** a client sends a request without `X-Tenant-Id` and `ratelimiter.fallback.ip.enabled` is set to `false`
- **THEN** the Gateway responds with HTTP 401 Unauthorized and does not proceed to token estimation or routing.
