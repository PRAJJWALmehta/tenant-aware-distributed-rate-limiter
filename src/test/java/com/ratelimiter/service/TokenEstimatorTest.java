package com.ratelimiter.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TokenEstimatorTest {

    private final TokenEstimator estimator = new TokenEstimator(10);

    @Test
    public void testExtractsMaxTokens() {
        String json = "{\"max_tokens\": 150, \"prompt\": \"Hello world\"}";
        int estimate = estimator.estimate(json.getBytes());
        assertEquals(150, estimate);
    }

    @Test
    public void testFallbackToPromptLengthPlusBuffer() {
        String json = "{\"prompt\": \"Hello world\"}";
        int estimate = estimator.estimate(json.getBytes());
        // "Hello world" length is 11, buffer is 10, total should be 21
        assertEquals(21, estimate);
    }

    @Test
    public void testFallbackWhenNoPromptOrMaxTokens() {
        String json = "{}";
        int estimate = estimator.estimate(json.getBytes());
        // prompt length is 0, buffer is 10, total should be 10
        assertEquals(10, estimate);
    }

    @Test
    public void testInvalidJsonThrowsException() {
        String json = "{invalid json";
        assertThrows(IllegalArgumentException.class, () -> {
            estimator.estimate(json.getBytes());
        });
    }
}
