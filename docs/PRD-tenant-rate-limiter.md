# PRD: Tenant-Aware Distributed Rate Limiter (API Gateway)

## Problem Statement
In multi-tenant systems, a "noisy neighbor" can exhaust shared resources, leading to outages for other tenants. Standard rate limiters restrict "requests per minute" indiscriminately. This project builds an intelligent, payload-aware API Gateway that limits based on "Tokens per Hour" (budget) by parsing outgoing JSON payloads, estimating cost, and securely enforcing distributed rate limits at 100,000+ RPS.

## Target Architecture & Tech Stack
Based on architectural best practices for 100k+ RPS systems:
- **Language/Framework:** Java 21+ with Spring WebFlux. 
- **State Management:** Redis (via Lettuce client). We will use a highly optimized **Token Bucket algorithm implemented as a single Lua Script**. 
- **JSON Processing:** Jackson Streaming API (`JsonParser`). To avoid OOM errors, we stream the payload, extract target fields, and forward the stream directly.

## Design Decisions
- **Tenant Identification:** Identified via the `X-Tenant-Id` HTTP header. 
- **Payload Fallbacks:** If `max_tokens` is missing from the payload, the cost is dynamically estimated based on the string length of the `prompt` plus a configurable default buffer.
- **Streaming & True-ups:** For simplicity and high throughput, the token cost is estimated upfront and deducted *before* routing the request. No asynchronous true-ups are performed on streamed responses.

## User Stories
1. **As a tenant**, I want my API requests to be routed efficiently to the upstream LLM service without noticeable latency.
2. **As an infrastructure engineer**, I want the gateway to inspect outgoing OpenAI requests, estimate the token cost, and decrement the tenant's hourly budget.
3. **As an infrastructure engineer**, I want requests that exceed the budget to be rejected immediately with an HTTP 429 status.
4. **As an application developer**, I want to easily integrate this by changing my base API URL to the Gateway.

## Edge Cases Handled
1. **Redis Unavailability:** Gateway "fails open" (allows traffic to pass) to prevent complete outage.
2. **Missing Payload Fields:** Falls back to prompt length estimation.
3. **Invalid JSON:** Rejected immediately with 400 Bad Request.
4. **Upstream Timeouts:** Strict timeouts enforced to return 504 Gateway Timeout.
