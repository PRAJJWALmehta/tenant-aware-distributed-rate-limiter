## 1. Token Estimation Parsing (Jackson Streaming)

- [x] 1.1 Write failing test `TokenEstimatorTest.java` to verify it correctly extracts `max_tokens` from JSON and verifies fallback logic calculates `prompt` length + buffer.
- [x] 1.2 Run test to verify it fails.
- [x] 1.3 Write implementation `TokenEstimator.java` using `JsonFactory` and `JsonParser` to non-blockingly parse byte arrays and extract token values.
- [x] 1.4 Run test to verify it passes.
- [ ] 1.5 Commit changes.
