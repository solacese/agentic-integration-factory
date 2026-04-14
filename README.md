# Agentic Integration Factory

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Tests](https://github.com/solacese/agentic-integration-factory/actions/workflows/test.yml/badge.svg)](https://github.com/solacese/agentic-integration-factory/actions/workflows/test.yml)
[![Python](https://img.shields.io/badge/python-3.13-blue.svg)](https://www.python.org/downloads/)
[![Code style: ruff](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/astral-sh/ruff/main/assets/badge/v2.json)](https://github.com/astral-sh/ruff)

Open-source starter repo for an agent-driven micro-integration factory:

`discover -> design -> generate -> validate -> deploy -> govern`

It turns **any source** — OpenAPI specs, JSON Schemas, database credentials, or custom inputs — into a production-ready **Solace MDK micro-integration** with canonical event models, workflow routing, and Event Portal governance.

## Key Capabilities

- **Source-agnostic ingestion** — pluggable source adapters handle parsing, summarization, and canonicalization for each input type
- **Real Solace MDK output** — generated projects use the `micro-integration-build-parent`, pre-created bindings, workflow routing, and `BindingCapabilitiesFactory` beans (based on `solace-mdk-samples-main`)
- **Multiple ingress patterns** — REST controllers, polling consumers, or event subscribers depending on the source type
- **Full lifecycle** — generation, build, deploy, test, Event Portal sync, and AI refinement in one pipeline
- **Agent-native** — comes with a skill definition and lifecycle runbook for autonomous operation

## Supported Source Types

| Source Type | Input | Ingress Pattern |
|-------------|-------|-----------------|
| **OpenAPI** | YAML/JSON spec | REST controller |
| **JSON Schema** | `.schema.json` file | REST controller (synthetic CRUD) |
| **Database** | Connection credentials | Polling consumer |
| **MQTT / Kafka** | Broker config | Event subscriber |
| **Custom** | Any structured input | Implement `SourceAdapter` |

Adding a new source type requires implementing three methods (`parse`, `summarize`, `canonicalize`) in a `SourceAdapter` subclass. The pipeline, generator, build, deploy, and governance layers need zero changes.

## Start Here

1. Pick a demo mode:

```bash
./scripts/use_demo_env.sh openapi
./scripts/use_demo_env.sh postgres
./scripts/use_demo_env.sh hybrid
```

2. Bootstrap the repo:

```bash
make bootstrap
```

3. Validate the active env:

```bash
make preflight
```

4. Start the local factory:

```bash
npm run dev
```

5. Or launch the public EC2 control plane:

```bash
make demo-ec2-up
```

## What Gets Generated

Every run produces a complete Solace MDK micro-integration project:

```
workspace/
├── pom.xml                          # MDK build parent (3.0.6)
├── Dockerfile                       # Multi-stage build with external config support
├── config/application-runtime.yml   # Workflow → destination mappings
├── src/main/resources/
│   └── application.yml              # MDK internal config (20 bindings, workflows, Solace defaults)
├── src/main/java/.../
│   ├── MicroIntegrationApplication.java           # Spring Boot + MDK bean declarations
│   ├── binding/
│   │   ├── SourceConsumerBindingCapabilitiesFactory.java  # Consumer ack mode
│   │   └── SourceProducerBindingCapabilitiesFactory.java  # Producer ack mode
│   ├── api/
│   │   └── GeneratedApiController.java            # REST ingress (when applicable)
│   └── service/
│       ├── CanonicalEventService.java             # Operation → event mapping
│       └── SolacePublisherService.java            # Solace StreamBridge publishing
├── helm/                            # Kubernetes Helm chart
└── scripts/demo-curls.sh            # Test fixtures
```

## Demo Bundles

All prepared demo inputs live in [demo](demo):

- OpenAPI demo env: [demo/env/openapi.env](demo/env/openapi.env)
- PostgreSQL demo env: [demo/env/postgres.env](demo/env/postgres.env)
- Hybrid demo env: [demo/env/hybrid.env](demo/env/hybrid.env)
- OpenAPI prompt: [demo/prompts/openapi_ec2_event_portal.md](demo/prompts/openapi_ec2_event_portal.md)
- PostgreSQL prompt: [demo/prompts/postgres_ec2_event_portal.md](demo/prompts/postgres_ec2_event_portal.md)
- Hybrid prompt: [demo/prompts/openapi_postgres_ec2_event_portal.md](demo/prompts/openapi_postgres_ec2_event_portal.md)

The helper script copies one of those env bundles into the active root `.env`.

## Sample Inputs

- [samples/openapi/petstore.yaml](samples/openapi/petstore.yaml) — simple Petstore API
- [samples/openapi/stripe-webhook-demo.yaml](samples/openapi/stripe-webhook-demo.yaml) — Stripe webhook integration
- [samples/json_schema/order.schema.json](samples/json_schema/order.schema.json) — e-commerce order schema

## Main Folders

- [SKILL.md](SKILL.md)
  — agent entry point
- [create_micro_integrations.md](create_micro_integrations.md)
  — lifecycle runbook
- [apps/api](apps/api)
  — FastAPI orchestrator, source adapters, and deployment adapters
- [apps/web](apps/web)
  — Next.js UI with source type selector
- [templates](templates)
  — Solace MDK and Helm generation templates
- [mdk-reference](mdk-reference)
  — local MDK baseline (from solace-mdk-samples-main)
- [samples](samples)
  — bundled source inputs (OpenAPI, JSON Schema)
- [references](references)
  — deeper design and Event Portal guidance

## Architecture

```
Source Input (OpenAPI, JSON Schema, DB, MQTT, ...)
  │
  ▼
Source Adapter  ─── parse() → summarize() → canonicalize()
  │
  ▼
Canonical Event Model  (operations, topics, schemas, test fixtures)
  │
  ▼
Generator Service  ─── Jinja2 templates → Solace MDK project
  │                     (workflows, bindings, capabilities factories)
  ▼
Build Pipeline  ─── Docker build (local or ECR)
  │
  ▼
Deploy Pipeline  ─── Docker / EC2 / Kubernetes
  │
  ▼
Event Portal Sync  ─── domains, apps, schemas, events
```

## Minimal Agent Prompt

```text
Use this repository as your skill and execution guide.

Read SKILL.md first, then create_micro_integrations.md.
Use the active .env file in the repo as the configuration contract.
Prefer the bundled implementation in apps/, templates/, and infra/ instead of rebuilding the control plane from scratch.

Take over the micro-integration lifecycle:
- discover the source
- design the canonical events
- generate or adapt the micro-integration
- validate and test it
- deploy it
- register and link it in Solace Event Portal
- write the operator and consumer notes

The run is only complete when runtime, governance, and documentation are all in place.
```

## Notes

- Use [.env.example](.env.example) as the base config template.
- Use [samples/openapi/petstore.yaml](samples/openapi/petstore.yaml) for the simplest API-first demo.
- Use [samples/json_schema/order.schema.json](samples/json_schema/order.schema.json) for a schema-first demo.
- Use [references/postgres_demo_source_notes.md](references/postgres_demo_source_notes.md) for the validated PostgreSQL source shape.
