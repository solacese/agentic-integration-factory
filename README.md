# Agentic Integration Factory

Open-source starter repo for an agent-driven micro-integration factory:

`discover -> design -> generate -> validate -> deploy -> govern`

It includes:

- a runnable control-plane app
- an agent skill and runbook
- bundled Solace MDK generator templates
- demo-ready OpenAPI and PostgreSQL inputs
- EC2, Solace, Event Portal, and LiteLLM demo bundles

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

## Demo Bundles

All prepared demo inputs live in [demo](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/demo):

- OpenAPI demo env: [demo/env/openapi.env](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/demo/env/openapi.env)
- PostgreSQL demo env: [demo/env/postgres.env](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/demo/env/postgres.env)
- Hybrid demo env: [demo/env/hybrid.env](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/demo/env/hybrid.env)
- OpenAPI prompt: [demo/prompts/openapi_ec2_event_portal.md](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/demo/prompts/openapi_ec2_event_portal.md)
- PostgreSQL prompt: [demo/prompts/postgres_ec2_event_portal.md](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/demo/prompts/postgres_ec2_event_portal.md)
- Hybrid prompt: [demo/prompts/openapi_postgres_ec2_event_portal.md](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/demo/prompts/openapi_postgres_ec2_event_portal.md)

The helper script copies one of those env bundles into the active root `.env`.

## Main Folders

- [SKILL.md](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/SKILL.md)
  - agent entry point
- [create_micro_integrations.md](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/create_micro_integrations.md)
  - lifecycle runbook
- [apps/api](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/apps/api)
  - FastAPI orchestrator and adapters
- [apps/web](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/apps/web)
  - Next.js UI
- [templates](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/templates)
  - MDK and Helm generation templates
- [samples](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/samples)
  - bundled source inputs
- [references](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/references)
  - deeper design and Event Portal guidance

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

- Use [.env.example](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/.env.example) as the base config template.
- Use [samples/openapi/petstore.yaml](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/samples/openapi/petstore.yaml) for the simplest API-first demo.
- Use [references/postgres_demo_source_notes.md](/Users/raphaelcaillon/Documents/github/agentic-integration-factory/references/postgres_demo_source_notes.md) for the validated PostgreSQL source shape.
