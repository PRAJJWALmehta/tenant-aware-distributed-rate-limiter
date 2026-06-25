package com.ratelimiter.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
public class TenantFilter implements WebFilter {

    public static final String TENANT_KEY = "tenantId";
    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private final boolean fallbackIpEnabled;

    public TenantFilter(@Value("${ratelimiter.fallback.ip.enabled:true}") boolean fallbackIpEnabled) {
        this.fallbackIpEnabled = fallbackIpEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (exchange.getRequest().getPath().value().equals("/health")) {
            return chain.filter(exchange);
        }

        String tenantId = exchange.getRequest().getHeaders().getFirst(TENANT_HEADER);
        if (tenantId != null && !tenantId.trim().isEmpty()) {
            String finalTenantId = tenantId.trim();
            return chain.filter(exchange)
                    .contextWrite(context -> context.put(TENANT_KEY, finalTenantId));
        }

        if (!fallbackIpEnabled) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String ip = null;
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR_HEADER);
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            String[] ips = xForwardedFor.split(",");
            if (ips.length > 0 && !ips[0].trim().isEmpty()) {
                ip = ips[0].trim();
            }
        }

        if (ip == null) {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress != null && remoteAddress.getAddress() != null) {
                ip = remoteAddress.getAddress().getHostAddress();
            }
        }

        if (ip != null) {
            String finalIp = ip;
            return chain.filter(exchange)
                    .contextWrite(context -> context.put(TENANT_KEY, finalIp));
        }

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
