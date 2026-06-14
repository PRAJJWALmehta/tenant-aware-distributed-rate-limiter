# Future Enhancements

- [ ] Migrate the Standalone API Gateway to a Sidecar deployment pattern to integrate natively with existing microservices.
- [ ] Add support for extracting the tenant identity from the API key inside the `Authorization: Bearer` header, dynamically looking up the tenant in Redis, instead of relying solely on the `X-Tenant-Id` header.
