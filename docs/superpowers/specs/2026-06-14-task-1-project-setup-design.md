# Design: Task 1 - Project Setup & Basic Proxy Routing

## Overview
This document outlines the foundation for the Tenant-Aware Distributed Rate Limiter. Task 1 focuses on setting up the initial Java Spring Boot WebFlux project, configuring standard dependencies, and establishing a basic health-check route. This ensures the foundational reactive web context is operational before introducing complex streaming and Redis interactions.

## Architecture & Frameworks
- **Platform**: Java 21+
- **Framework**: Spring Boot 3.x with Spring WebFlux
- **Routing Approach**: Pure Spring WebFlux with custom routing logic using `WebClient`. This approach was selected to provide maximum control over the Jackson Streaming API for non-blocking payload parsing in subsequent tasks, avoiding the memory overhead of fully buffering the request.
- **State Management**: Redis (Lettuce Client), dependency added in preparation for Task 4.

## Components

### 1. Build Configuration (`pom.xml`)
We will use Maven for dependency management. The `pom.xml` will include:
- `spring-boot-starter-webflux`: Core dependency for the reactive server (Netty) and reactive web capabilities.
- `spring-boot-starter-data-redis-reactive`: Provides the Lettuce client for non-blocking Redis interactions.
- `spring-boot-starter-test`: Standard testing utilities.
- `reactor-test`: Utilities for testing Project Reactor components (e.g., `StepVerifier`).

### 2. Application Core (`GatewayApplication.java`)
The main entry point for the application.
- Annotated with `@SpringBootApplication`.
- Will define a simple `RouterFunction` bean to handle the `/health` endpoint.

### 3. Application Configuration (`application.yml`)
Initial configuration settings:
- `server.port`: The port the gateway will listen on (default 8080).
- `spring.data.redis.*`: Placeholder configurations for the Redis connection.

### 4. Routing & Endpoints
- **Health Check**: `GET /health` -> Returns `200 OK` with a plain text body of `"OK"`. This is implemented using WebFlux's functional routing or a simple `@RestController` to verify the application context and web server start correctly.

## Data Flow
1. Client sends a `GET` request to `/health`.
2. Netty receives the request and dispatches it via the Spring WebFlux event loop.
3. The `RouterFunction` handles the request and returns a `ServerResponse` with the body `"OK"`.

## Testing Strategy
- **Framework**: JUnit 5, Spring Boot Test.
- **Test Class**: `GatewayRoutingTest.java`
- **Methodology**: Integration testing using `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`.
- **Validation**: A `WebTestClient` will be auto-configured and injected to issue a real HTTP `GET` request to `/health`. The test will assert that the HTTP status is `200 OK` and the response body strictly matches `"OK"`.

## Error Handling
For this initial task, standard Spring Boot WebFlux error handling will be utilized. Unhandled exceptions during startup will fail the application context loading, which is expected and desired behavior for a fail-fast startup.

## Next Steps
Following the implementation of this design, the project will be ready to tackle Task 2 (Tenant Identification Filter) using the established WebFlux foundation.
