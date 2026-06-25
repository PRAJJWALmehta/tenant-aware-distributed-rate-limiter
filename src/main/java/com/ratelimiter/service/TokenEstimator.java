package com.ratelimiter.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class TokenEstimator {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private final int defaultBuffer;

    public TokenEstimator(@Value("${ratelimiter.token.fallback-buffer:10}") int defaultBuffer) {
        this.defaultBuffer = defaultBuffer;
    }

    /**
     * Estimates token consumption from a JSON payload.
     * Uses Jackson Streaming API for non-blocking in-memory parsing.
     * Extracts "max_tokens" if present, otherwise falls back to prompt length + defaultBuffer.
     *
     * @param payload JSON byte array
     * @return Estimated token count
     * @throws IllegalArgumentException if the JSON is malformed
     */
    public int estimate(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return defaultBuffer;
        }

        Integer maxTokens = null;
        String prompt = null;

        try (JsonParser parser = JSON_FACTORY.createParser(payload)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Invalid JSON: root must be an object");
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();

                if (fieldName != null) {
                    if ("max_tokens".equals(fieldName)) {
                        if (valueToken != null && valueToken.isNumeric()) {
                            maxTokens = parser.getIntValue();
                        }
                    } else if ("prompt".equals(fieldName)) {
                        if (valueToken == JsonToken.VALUE_STRING) {
                            prompt = parser.getText();
                        }
                    } else {
                        // Skip children of nested structures to stay at the root level
                        if (valueToken == JsonToken.START_OBJECT || valueToken == JsonToken.START_ARRAY) {
                            parser.skipChildren();
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed JSON payload", e);
        }

        if (maxTokens != null) {
            return maxTokens;
        }

        int promptLength = (prompt != null) ? prompt.length() : 0;
        return promptLength + defaultBuffer;
    }
}
