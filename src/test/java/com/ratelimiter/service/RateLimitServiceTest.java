package com.ratelimiter.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class RateLimitServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("ratelimiter.bucket.capacity", () -> 100);
        registry.add("ratelimiter.bucket.refill-rate", () -> 10);
    }

    @Autowired
    private RateLimitService rateLimitService;

    @Test
    void testSuccessfulTokenDeduction() {
        StepVerifier.create(rateLimitService.isAllowed("tenant-success", 30))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(rateLimitService.isAllowed("tenant-success", 50))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testRejectionWhenTokensInsufficientAndNoPartialDeduction() {
        // Capacity 100: deduct 90 -> 10 left
        StepVerifier.create(rateLimitService.isAllowed("tenant-reject", 90))
                .expectNext(true)
                .verifyComplete();

        // Request 20 when only 10 left -> denied
        StepVerifier.create(rateLimitService.isAllowed("tenant-reject", 20))
                .expectNext(false)
                .verifyComplete();

        // Verify remaining tokens were not deducted: requesting 10 should succeed
        StepVerifier.create(rateLimitService.isAllowed("tenant-reject", 10))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testBucketRefillAfterElapsedTime() throws InterruptedException {
        // Exhaust all 100 tokens
        StepVerifier.create(rateLimitService.isAllowed("tenant-refill", 100))
                .expectNext(true)
                .verifyComplete();

        // Immediately request more -> denied
        StepVerifier.create(rateLimitService.isAllowed("tenant-refill", 10))
                .expectNext(false)
                .verifyComplete();

        // Refill rate is 10 tokens/sec. Wait 1.2s to refill ~12 tokens (at least 10)
        Thread.sleep(1200);

        StepVerifier.create(rateLimitService.isAllowed("tenant-refill", 10))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testFirstRequestInitializationAtFullCapacity() {
        // Brand new tenant requesting full capacity (100) -> allowed
        StepVerifier.create(rateLimitService.isAllowed("tenant-init-full", 100))
                .expectNext(true)
                .verifyComplete();

        // Brand new tenant requesting more than capacity (101) -> denied
        StepVerifier.create(rateLimitService.isAllowed("tenant-init-excess", 101))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testInputValidation() {
        StepVerifier.create(rateLimitService.isAllowed(null, 10))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(rateLimitService.isAllowed("", 10))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(rateLimitService.isAllowed("   ", 10))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(rateLimitService.isAllowed("tenant-valid", 0))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(rateLimitService.isAllowed("tenant-valid", -5))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void testConcurrentRequestsAtomicDeduction() {
        // Capacity is 100. 20 concurrent requests of 10 tokens each.
        // Exactly 10 should succeed (10 * 10 = 100), and exactly 10 should be denied.
        List<Boolean> results = Flux.range(1, 20)
                .flatMap(i -> rateLimitService.isAllowed("tenant-concurrent", 10))
                .collectList()
                .block();

        long allowedCount = results.stream().filter(Boolean::booleanValue).count();
        long deniedCount = results.stream().filter(b -> !b).count();

        assertEquals(10, allowedCount, "Exactly 10 requests should be allowed for capacity 100");
        assertEquals(10, deniedCount, "Remaining 10 requests should be denied");
    }
}
