## ADDED Requirements

### Requirement: Atomic token deduction via Lua script
The system SHALL atomically check and deduct a requested number of tokens from a per-tenant Redis token bucket in a single operation, preventing race conditions under concurrent access.

#### Scenario: Successful deduction with sufficient tokens
- **WHEN** a tenant's bucket contains 100 tokens and a request requires 30 tokens
- **THEN** the system deducts 30 tokens, the bucket contains 70 tokens, and the request is allowed.

#### Scenario: Rejected deduction with insufficient tokens
- **WHEN** a tenant's bucket contains 10 tokens and a request requires 50 tokens
- **THEN** the system does NOT deduct any tokens and the request is denied.

### Requirement: Token bucket refill over time
The system SHALL refill the token bucket based on elapsed time since the last refill, at a configurable rate per second, capped at the maximum bucket capacity.

#### Scenario: Tokens refill after idle period
- **WHEN** a tenant's bucket was last accessed 10 seconds ago and the refill rate is 10 tokens/second with a capacity of 100
- **THEN** the bucket is refilled by 100 tokens (capped at capacity) before processing the current request.

#### Scenario: Partial refill does not exceed capacity
- **WHEN** a tenant's bucket has 90 tokens, 2 seconds have passed, and the refill rate is 10 tokens/second with capacity 100
- **THEN** the bucket is refilled to 100 tokens (not 110), capping at capacity.

### Requirement: Bucket initialization on first access
The system SHALL initialize a new tenant's bucket at full capacity on the first request, requiring no pre-provisioning.

#### Scenario: First request for unknown tenant
- **WHEN** a request arrives for a tenant with no existing bucket in Redis
- **THEN** the system creates a bucket at full capacity and processes the deduction normally.

### Requirement: Reactive async execution
The system SHALL execute the Redis Lua script asynchronously via `ReactiveRedisTemplate`, returning a reactive type (`Mono<Boolean>`) indicating allow or deny.

#### Scenario: Non-blocking rate limit check
- **WHEN** the `RateLimitService.isAllowed(tenantId, tokens)` method is called
- **THEN** it returns a `Mono<Boolean>` that completes without blocking the calling thread.
