## 1. Project Initialization

- [x] 1.1 Create `pom.xml` with Spring Boot 3 WebFlux, Reactive Redis, and testing dependencies
- [x] 1.2 Create `src/main/resources/application.yml` with basic configurations (server port 8080, redis placeholders)

## 2. Application Core

- [x] 2.1 Create `src/main/java/com/ratelimiter/GatewayApplication.java` with `@SpringBootApplication`
- [x] 2.2 Add a functional router bean for the `GET /health` endpoint that returns a 200 OK with body "OK"

## 3. Testing

- [x] 3.1 Create `src/test/java/com/ratelimiter/GatewayRoutingTest.java`
- [x] 3.2 Write an integration test using `@SpringBootTest` and `WebTestClient` to verify the `/health` endpoint returns a 200 status code and "OK" response body
