## ADDED Requirements

### Requirement: Token Extractor reads max_tokens
The system SHALL parse the JSON payload to extract `max_tokens` if it exists.

#### Scenario: Request has max_tokens
- **WHEN** the request JSON contains a top-level `max_tokens` integer field
- **THEN** the token estimator returns that integer value.

### Requirement: Token Extractor uses prompt fallback
The system SHALL fallback to using the `prompt` string length to estimate tokens if `max_tokens` is absent.

#### Scenario: Request has only prompt
- **WHEN** the request JSON contains a `prompt` field but no `max_tokens`
- **THEN** the token estimator returns an estimation based on the `prompt` length (e.g., prompt length + a configurable buffer).
