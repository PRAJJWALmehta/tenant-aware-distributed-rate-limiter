package com.ratelimiter.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class TenantFilterTest {

    @Test
    public void testValidTenantHeader() {
        TenantFilter filter = new TenantFilter(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").header("X-Tenant-Id", "tenant-abc")
        );

        filter.filter(exchange, new WebFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange ex) {
                return Mono.deferContextual(ctx -> {
                    String tenant = ctx.getOrDefault(TenantFilter.TENANT_KEY, null);
                    assertEquals("tenant-abc", tenant);
                    return Mono.empty();
                });
            }
        }).block();
    }

    @Test
    public void testHeaderMissingFallbackEnabledXForwardedFor() {
        TenantFilter filter = new TenantFilter(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").header("X-Forwarded-For", "192.168.1.50, 10.0.0.1")
        );

        filter.filter(exchange, new WebFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange ex) {
                return Mono.deferContextual(ctx -> {
                    String tenant = ctx.getOrDefault(TenantFilter.TENANT_KEY, null);
                    assertEquals("192.168.1.50", tenant);
                    return Mono.empty();
                });
            }
        }).block();
    }

    @Test
    public void testHeaderMissingFallbackEnabledRemoteAddress() {
        TenantFilter filter = new TenantFilter(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test").remoteAddress(new InetSocketAddress("10.10.10.10", 8080))
        );

        filter.filter(exchange, new WebFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange ex) {
                return Mono.deferContextual(ctx -> {
                    String tenant = ctx.getOrDefault(TenantFilter.TENANT_KEY, null);
                    assertEquals("10.10.10.10", tenant);
                    return Mono.empty();
                });
            }
        }).block();
    }

    @Test
    public void testHeaderMissingFallbackDisabled() {
        TenantFilter filter = new TenantFilter(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
        );

        filter.filter(exchange, ex -> Mono.error(new AssertionError("Chain should not be called")))
                .block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    public void testHealthCheckBypassedEvenIfFallbackDisabled() {
        TenantFilter filter = new TenantFilter(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/health")
        );

        filter.filter(exchange, new WebFilterChain() {
            @Override
            public Mono<Void> filter(ServerWebExchange ex) {
                return Mono.deferContextual(ctx -> {
                    assertFalse(ctx.hasKey(TenantFilter.TENANT_KEY));
                    return Mono.empty();
                });
            }
        }).block();

        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }
}
