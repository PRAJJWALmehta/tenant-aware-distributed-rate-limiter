package com.ratelimiter;

import com.ratelimiter.filter.TenantFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class GatewayIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("ratelimiter.bucket.capacity", () -> 100);
        registry.add("ratelimiter.bucket.refill-rate", () -> 10);
        registry.add("ratelimiter.token.fallback-buffer", () -> 10);
    }

    @Autowired
    private WebTestClient webClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Test
    void testSuccessfulPostRequestDeductsTokensAndRoutes() {
        String tenantId = "tenant-integration-success";

        // Initial capacity is 100. Deduct 25 tokens.
        webClient.post().uri("/v1/chat/completions")
                .header(TenantFilter.TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"max_tokens\": 25, \"prompt\": \"Hello world\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("OK");

        // Verify Redis bucket decreased from 100 to 75
        StepVerifier.create(redisTemplate.opsForHash().get("rate_limit:" + tenantId, "tokens"))
                .expectNext("75")
                .verifyComplete();

        // Second request uses prompt length (2) + fallback buffer (10) = 12 tokens
        webClient.post().uri("/v1/chat/completions")
                .header(TenantFilter.TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"prompt\": \"Hi\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("OK");

        // Verify Redis bucket decreased from 75 to 63
        StepVerifier.create(redisTemplate.opsForHash().get("rate_limit:" + tenantId, "tokens"))
                .expectNext("63")
                .verifyComplete();
    }

    @Test
    void testRateLimitExceededReturns429AndNoDeduction() {
        String tenantId = "tenant-integration-exhaust";

        // Deduct 90 tokens out of 100 -> 10 left
        webClient.post().uri("/v1/chat/completions")
                .header(TenantFilter.TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"max_tokens\": 90}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("OK");

        StepVerifier.create(redisTemplate.opsForHash().get("rate_limit:" + tenantId, "tokens"))
                .expectNext("10")
                .verifyComplete();

        // Request 20 tokens when only 10 left -> 429 Too Many Requests
        webClient.post().uri("/v1/chat/completions")
                .header(TenantFilter.TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"max_tokens\": 20}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verify Redis balance remains 10 (no partial deduction)
        StepVerifier.create(redisTemplate.opsForHash().get("rate_limit:" + tenantId, "tokens"))
                .expectNext("10")
                .verifyComplete();
    }

    @Test
    void testMalformedJsonPayloadReturns400BadRequest() {
        String tenantId = "tenant-integration-malformed";

        webClient.post().uri("/v1/chat/completions")
                .header(TenantFilter.TENANT_HEADER, tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("not a valid json")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testTenantIdentificationFallbackToIpWhenHeaderMissing() {
        String clientIp = "198.51.100.1";

        webClient.post().uri("/v1/chat/completions")
                .header(TenantFilter.X_FORWARDED_FOR_HEADER, clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"max_tokens\": 15}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("OK");

        // Verify Redis bucket created for client IP with 100 - 15 = 85 tokens
        StepVerifier.create(redisTemplate.opsForHash().get("rate_limit:" + clientIp, "tokens"))
                .expectNext("85")
                .verifyComplete();
    }

    @Test
    void testHealthEndpointRemainsAccessible() {
        webClient.get().uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("OK");
    }
}
