## Why

To support multi-tenancy in our distributed rate limiter, we need to be able to identify which tenant a given request belongs to. Extracting the `X-Tenant-Id` header allows us to scope rate limits appropriately and ensure unauthorized requests without tenant identification are rejected.

## What Changes

- Create a new Spring WebFilter (`TenantFilter`) that inspects incoming requests.
- Extract the `X-Tenant-Id` HTTP header.
- If the header is missing, fall back to extracting the client's IP address.
- Provide a configuration toggle (`ratelimiter.fallback.ip.enabled`) to disable this fallback and return 401 Unauthorized during DDoS attacks.
- Add the extracted tenant ID or the fallback IP address to the reactor context for downstream processing.

## Capabilities

### New Capabilities
- `tenant-identification`: Extracts tenant ID from headers and validates its presence.

### Modified Capabilities

## Impact

- **Affected Code:** Gateway layer filtering, downstream reactive pipelines that will now have access to the tenant ID via reactor context.
- **APIs:** Incoming API requests without an `X-Tenant-Id` header will automatically be rate-limited based on their IP address instead of returning a 401 error.
