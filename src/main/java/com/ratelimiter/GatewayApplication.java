package com.ratelimiter;

import com.ratelimiter.filter.TenantFilter;
import com.ratelimiter.service.RateLimitService;
import com.ratelimiter.service.TokenEstimator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@SpringBootApplication
public class GatewayApplication {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GatewayApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> healthRouter() {
        return RouterFunctions.route(GET("/health"), 
            request -> ServerResponse.ok().bodyValue("OK"));
    }

    @Bean
    public RouterFunction<ServerResponse> apiRouter(
            RateLimitService rateLimitService, 
            TokenEstimator tokenEstimator,
            @Value("${upstream.url:}") String upstreamUrl) {

        final WebClient webClient = (upstreamUrl != null && !upstreamUrl.isBlank())
                ? WebClient.builder().baseUrl(upstreamUrl).build()
                : null;

        return RouterFunctions.route(POST("/**"), request -> {
            long startNs = System.nanoTime();
            return Mono.deferContextual(ctx -> {
                String tenantId = ctx.getOrDefault(TenantFilter.TENANT_KEY, null);
                if (tenantId == null || tenantId.isBlank()) {
                    return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
                }

                return request.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .flatMap(body -> {
                            int tokens;
                            try {
                                tokens = tokenEstimator.estimate(body);
                            } catch (IllegalArgumentException e) {
                                return ServerResponse.badRequest().bodyValue(e.getMessage());
                            }

                            if (tokens <= 0) {
                                return ServerResponse.badRequest().bodyValue("Requested tokens must be greater than zero");
                            }

                            return rateLimitService.isAllowed(tenantId, tokens)
                                    .flatMap(allowed -> {
                                        if (allowed) {
                                            if (webClient != null) {
                                                long upstreamStartNs = System.nanoTime();
                                                return webClient.post()
                                                        .uri(request.path())
                                                        .contentType(request.headers().contentType().orElse(MediaType.APPLICATION_JSON))
                                                        .bodyValue(body)
                                                        .exchangeToMono(clientResponse -> 
                                                            clientResponse.bodyToMono(byte[].class)
                                                                    .defaultIfEmpty(new byte[0])
                                                                    .flatMap(upstreamBody -> {
                                                                        long upstreamDurationNs = System.nanoTime() - upstreamStartNs;
                                                                        long totalDurationNs = System.nanoTime() - startNs;
                                                                        long overheadNs = Math.max(0, totalDurationNs - upstreamDurationNs);
                                                                        double overheadMs = overheadNs / 1_000_000.0;
                                                                        return ServerResponse.status(clientResponse.statusCode())
                                                                                .headers(headers -> {
                                                                                    clientResponse.headers().asHttpHeaders().forEach((name, values) -> {
                                                                                        if (!name.equalsIgnoreCase("Transfer-Encoding") && !name.equalsIgnoreCase("Content-Length")) {
                                                                                            headers.addAll(name, values);
                                                                                        }
                                                                                    });
                                                                                    headers.set("X-Gateway-Overhead-Ms", String.format(java.util.Locale.US, "%.2f", overheadMs));
                                                                                })
                                                                                .bodyValue(upstreamBody);
                                                                    })
                                                        )
                                                        .onErrorResume(e -> {
                                                            log.error("Upstream proxy request failed for tenant {}", tenantId, e);
                                                            return ServerResponse.status(HttpStatus.BAD_GATEWAY)
                                                                    .bodyValue("Bad Gateway");
                                                        });
                                            }
                                            long overheadNs = Math.max(0, System.nanoTime() - startNs);
                                            double overheadMs = overheadNs / 1_000_000.0;
                                            return ServerResponse.ok()
                                                    .header("X-Gateway-Overhead-Ms", String.format(java.util.Locale.US, "%.2f", overheadMs))
                                                    .bodyValue("OK");
                                        } else {
                                            long overheadNs = Math.max(0, System.nanoTime() - startNs);
                                            double overheadMs = overheadNs / 1_000_000.0;
                                            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                                                    .header("X-Gateway-Overhead-Ms", String.format(java.util.Locale.US, "%.2f", overheadMs))
                                                    .bodyValue("Rate limit exceeded");
                                        }
                                    })
                                    .onErrorResume(e -> {
                                        log.error("Rate limit check failed for tenant {}", tenantId, e);
                                        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                                .bodyValue("Rate limiter service unavailable");
                                    });
                        });
            });
        });
    }
}
