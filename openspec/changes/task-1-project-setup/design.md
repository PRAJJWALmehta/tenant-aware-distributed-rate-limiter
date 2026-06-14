## Context

The Tenant-Aware Distributed Rate Limiter requires a robust, non-blocking foundation to handle high throughput (100,000+ RPS) while processing large JSON payloads. Task 1 focuses on setting up the initial Java Spring Boot WebFlux project, configuring standard dependencies, and establishing a basic health-check route. This ensures the foundational reactive web context is operational before introducing complex streaming and Redis interactions.

## Goals / Non-Goals

**Goals:**
- Create the initial project scaffold using Maven and Java 21.
- Introduce Spring Boot WebFlux, Spring Data Redis Reactive, and testing dependencies.
- Verify basic routing logic with a health endpoint.
- Setup integration testing foundation with `WebTestClient`.

**Non-Goals:**
- Implementing the Redis Lua script for token deduction.
- Parsing streaming JSON payloads.
- Implementing tenant identification headers.
- Establishing external upstream connections.

## Decisions

- **Framework**: Spring Boot 3.x with Spring WebFlux. Selected over Spring Web MVC to support non-blocking I/O and reactive streaming, essential for high throughput without thread exhaustion.
- **Routing Approach**: Pure Spring WebFlux with custom routing logic using `WebClient`. This approach was selected instead of Spring Cloud Gateway to provide maximum control over the Jackson Streaming API for non-blocking payload parsing in subsequent tasks, avoiding the memory overhead of fully buffering requests.
- **Route Separation**: All routing definitions will be isolated in dedicated configuration classes under a separate package (e.g., `RouterConfig` under `com.ratelimiter.config`) to keep the application bootstrap class clean and modular.
- **State Management Preparedness**: `spring-boot-starter-data-redis-reactive` is included now to establish the Lettuce client foundation for future distributed state management.

## Risks / Trade-offs

- **Risk: Reactive Programming Complexity**: The reactive paradigm has a steeper learning curve and can be harder to debug. 
  - **Mitigation**: Rely on comprehensive integration tests using `WebTestClient` and `reactor-test` to validate flows early and often.
- **Trade-off: Pure WebFlux vs Spring Cloud Gateway**: We are foregoing the built-in routing and filtering capabilities of Spring Cloud Gateway in favor of finer-grained stream control. This means we must build routing logic manually, but it guarantees minimal memory footprint during payload inspection.
