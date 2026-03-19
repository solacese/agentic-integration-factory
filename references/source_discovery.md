# Source Discovery

Use this file when the source is not already a clean OpenAPI contract, or when the agent must decide how to build the connector.

## OpenAPI Discovery

Inspect:
- title, version, servers
- operations, request and response schemas
- auth schemes
- tags and webhook-like operations

Infer:
- event-producing operations
- canonical entities
- sample payloads
- candidate topics

## Database Discovery

Inspect:
- database kind and version
- schemas and tables
- primary keys and foreign keys
- created or updated timestamps
- soft-delete patterns
- row counts and change volumes
- CDC capability

Infer:
- source entities
- lifecycle transitions
- polling, CDC, or trigger strategy
- idempotency keys
- event ordering requirements

## Existing Connector Reuse

Search for:
- existing micro-integrations
- reusable adapters
- polling or CDC templates
- schema mappers
- prior generated runs for similar domains

Decision order:
1. reuse as-is
2. adapt with minimal changes
3. generate new

## Output Contract

Discovery must produce:
- source summary
- chosen connector pattern
- risk notes
- proposed event set
- proposed deployment pattern

## Guardrails

- Use read-only credentials first.
- Do not mutate source systems during discovery.
- Do not guess schema semantics when the source can be inspected directly.
- Flag unclear ownership, missing timestamps, or poor CDC support as risks.
