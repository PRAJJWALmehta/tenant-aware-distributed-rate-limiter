# tenant-identification Specification

## Purpose
TBD - created by archiving change tenant-identification-filter. Update Purpose after archive.
## Requirements
### Requirement: Identify tenant from request headers
The Gateway MUST extract the `X-Tenant-Id` header from all incoming requests to identify the tenant. If the header is missing, it MUST fall back to identifying the client by IP address, UNLESS the IP fallback toggle is disabled.

#### Scenario: Request missing tenant ID header with IP fallback disabled
- **WHEN** a request arrives without an `X-Tenant-Id` header AND the IP fallback toggle (`ratelimiter.fallback.ip.enabled`) is set to `false`
- **THEN** the request is rejected with a 401 Unauthorized HTTP status code

#### Scenario: Request with valid tenant ID header
- **WHEN** a request arrives with an `X-Tenant-Id` header (e.g., `X-Tenant-Id: my-tenant-123`)
- **THEN** the tenant ID is extracted and added to the Reactor Context for downstream processing

#### Scenario: Request missing tenant ID header with X-Forwarded-For
- **WHEN** a request arrives without an `X-Tenant-Id` header but includes an `X-Forwarded-For` header
- **THEN** the first IP in the `X-Forwarded-For` header is extracted and added to the Reactor Context as the tenant identifier

#### Scenario: Request missing tenant ID header and X-Forwarded-For
- **WHEN** a request arrives without an `X-Tenant-Id` header and without an `X-Forwarded-For` header
- **THEN** the remote connection IP address is extracted and added to the Reactor Context as the tenant identifier

