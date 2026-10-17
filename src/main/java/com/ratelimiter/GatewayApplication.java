package com.ratelimiter;

import com.ratelimiter.filter.TenantFilter;
import com.ratelimiter.service.RateLimitService;
import com.ratelimiter.service.TokenEstimator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
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
    public RouterFunction<ServerResponse> apiRouter(RateLimitService rateLimitService, TokenEstimator tokenEstimator) {
        return RouterFunctions.route(POST("/**"), request ->
            Mono.deferContextual(ctx -> {
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
                                            return ServerResponse.ok().bodyValue("OK");
                                        } else {
                                            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                                                    .bodyValue("Rate limit exceeded");
                                        }
                                    })
                                    .onErrorResume(e -> {
                                        log.error("Rate limit check failed for tenant {}", tenantId, e);
                                        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                                .bodyValue("Rate limiter service unavailable");
                                    });
                        });
            })
        );
    }
}
