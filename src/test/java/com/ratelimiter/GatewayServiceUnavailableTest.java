package com.ratelimiter;

import com.ratelimiter.filter.TenantFilter;
import com.ratelimiter.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GatewayServiceUnavailableTest {

    @Autowired
    private WebTestClient webClient;

    @MockBean
    private RateLimitService rateLimitService;

    @Test
    void testRedisFailureReturns503ServiceUnavailable() {
        when(rateLimitService.isAllowed(anyString(), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

        webClient.post().uri("/v1/chat/completions")
                .header(TenantFilter.TENANT_HEADER, "tenant-test")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"max_tokens\": 10}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(String.class).isEqualTo("Rate limiter service unavailable");
    }
}
