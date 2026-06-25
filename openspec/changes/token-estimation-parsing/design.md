## Context

The API gateway handles incoming requests for rate limiting. Some tenants might be rate limited by tokens rather than just requests per minute. For requests going to LLMs, the request payload contains either a `max_tokens` field (which specifies the tokens explicitly) or a text `prompt` which needs to be estimated. Parsing the full JSON body into an object tree is memory intensive and blocking, which negatively impacts a Spring Cloud Gateway (reactive) environment.

## Goals / Non-Goals

**Goals:**
- Parse incoming JSON payloads non-blockingly as a byte stream.
- Fast extraction of `max_tokens`.
- Fast extraction of `prompt` string length.
- Calculate an estimated token cost using a predefined heuristic (e.g., length of prompt / 4 + max_tokens, or just max_tokens, or fallback buffer).

**Non-Goals:**
- Exact tokenization using BPE or Tiktoken. This is an estimation service.
- Modifying the request payload.

## Decisions

- **Jackson Streaming API (JsonFactory & JsonParser)**: Jackson provides a low-level streaming API that can process tokens (`JsonToken.START_OBJECT`, `JsonToken.FIELD_NAME`, etc.) without loading the whole JSON in memory.
- **Reactive Stream Integration**: The service will read `DataBuffer` from Spring WebFlux and feed it to the parser, returning a Mono with the estimated token count.

## Risks / Trade-offs

- **Risk: Malformed JSON** → Mitigation: Return a default fallback token count or fail the request with 400 Bad Request.
- **Risk: Inaccurate Estimation** → Mitigation: Use a conservative multiplier (like length/3 or +buffer) to ensure we don't under-charge the rate limit bucket.
