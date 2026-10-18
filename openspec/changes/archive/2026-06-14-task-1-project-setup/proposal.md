## Why

The foundation of the Tenant-Aware Distributed Rate Limiter must be established before we can implement complex features like payload streaming and token cost calculation. This initial setup provides the reactive web context and basic routing structure required for the gateway to operate.

## What Changes

- Initial project scaffold with Maven and Java 21.
- Inclusion of Spring Boot WebFlux, Spring Data Redis Reactive, and test dependencies.
- Creation of the core application entry point (`GatewayApplication.java`).
- Implementation of a basic `/health` endpoint to verify routing logic.
- Establishment of the testing framework with JUnit 5 and WebTestClient.

## Capabilities

### New Capabilities
- `project-setup`: Core infrastructure and foundational routing for the API gateway.

### Modified Capabilities

## Impact

- Creates the base Spring Boot application structure.
- Establishes the dependency baseline (`pom.xml`).
- Sets up initial configuration properties (`application.yml`).
