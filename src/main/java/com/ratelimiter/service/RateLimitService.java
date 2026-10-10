package com.ratelimiter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Service
public class RateLimitService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> script;
    private final int capacity;
    private final int refillRate;

    public RateLimitService(
            ReactiveRedisTemplate<String, String> redisTemplate,
            @Value("${ratelimiter.bucket.capacity:100}") int capacity,
            @Value("${ratelimiter.bucket.refill-rate:10}") int refillRate) {
        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.refillRate = refillRate;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setLocation(new ClassPathResource("scripts/token_bucket.lua"));
        redisScript.setResultType(Long.class);
        this.script = redisScript;
    }

    public Mono<Boolean> isAllowed(String tenantId, int requestedTokens) {
        String key = "rate_limit:" + tenantId;
        List<String> keys = Collections.singletonList(key);
        List<String> args = List.of(
                String.valueOf(requestedTokens),
                String.valueOf(capacity),
                String.valueOf(refillRate)
        );

        return redisTemplate.execute(script, keys, args)
                .next()
                .map(result -> result == 1L)
                .defaultIfEmpty(false);
    }
}
