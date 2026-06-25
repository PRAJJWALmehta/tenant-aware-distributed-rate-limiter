## Why

We need to accurately estimate token consumption for incoming requests to properly rate limit LLM API calls. A fast, non-blocking Jackson streaming parser allows us to efficiently parse large JSON request bodies (such as extracting `max_tokens` or the `prompt` length) without reading the entire payload into memory, improving performance and reducing memory footprint in the API gateway.

## What Changes

- Implement non-blocking JSON parsing using Jackson's `JsonFactory` and `JsonParser`.
- Add token extraction logic to read `max_tokens` if provided in the payload.
- Implement fallback logic that estimates tokens based on the `prompt` length plus a buffer.
- Add comprehensive unit tests covering successful extraction and fallback scenarios.

## Capabilities

### New Capabilities
- `token-estimation`: Parsing JSON payload streams non-blockingly to extract `max_tokens` or estimate based on prompt text.

### Modified Capabilities

## Impact

- Introduces a new `TokenEstimator` service.
- Will be integrated into the request filtering chain to calculate costs before applying rate limits.
- Requires Jackson dependencies (already present in typical Spring WebFlux applications).
