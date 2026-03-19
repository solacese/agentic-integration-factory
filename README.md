# Agentic Integration Factory

An agent skill repo for taking a micro-integration through the full lifecycle:

**discover -> design -> generate -> validate -> deploy -> govern -> document -> operate**

This repo is meant to be handed to a coding agent together with:

- an OpenAPI file or database credentials
- runtime deployment credentials for EC2 or Kubernetes
- Solace PubSub+ broker credentials
- Solace Event Portal API credentials
- optional AI endpoint credentials

The agent should then be able to own the full lifecycle of the micro-integration.

## What This Repo Contains

- `SKILL.md`
  - the skill entry point
- `create_micro_integrations.md`
  - the main lifecycle runbook
- `references/`
  - source discovery, HA deployment, lifecycle, and Event Portal linking guidance
- `.env.template`
  - the configuration contract the agent should expect
- `agents/openai.yaml`
  - UI metadata for agent skill lists

## What This Repo Does

This repo gives an agent the instructions and structure to:

1. ingest an OpenAPI contract or source credentials
2. discover the source system
3. derive a canonical event model
4. reuse or generate a Solace MDK-based micro-integration
5. validate and test it
6. deploy it to EC2 or Kubernetes
7. reconcile the resulting application, schemas, and events into Solace Event Portal
8. produce documentation and operational handoff notes

## What You Need

### Required input

Choose at least one source mode:

- `OpenAPI`
  - a local file path or URL to the OpenAPI spec
- `Database`
  - host, port, database, schema, username, password, and SSL mode

### Required runtime credentials

- Solace broker credentials:
  - `SOLACE_BROKER_URL`
  - `SOLACE_VPN`
  - `SOLACE_USERNAME`
  - `SOLACE_PASSWORD`
- Solace Event Portal credentials:
  - `EVENT_PORTAL_BASE_URL`
  - `EVENT_PORTAL_TOKEN`

### Required deployment credentials

Choose one target:

- `EC2 / AWS`
  - `AWS_REGION`
  - `AWS_ACCESS_KEY_ID`
  - `AWS_SECRET_ACCESS_KEY`
  - `AWS_SESSION_TOKEN` when applicable
- `Kubernetes / Rancher`
  - `K8S_API_SERVER`
  - `K8S_TOKEN`
  - `K8S_NAMESPACE`
  - `K8S_CA_CERT`
  - optional `RANCHER_URL`
  - optional `RANCHER_TOKEN`

### Optional

- AI refinement endpoint:
  - `LITELLM_BASE_URL`
  - `LITELLM_API_KEY`
  - `LITELLM_MODEL`
- schema or SerDes registry
- container registry credentials if not using an auto-managed registry path

## Quick Start

1. Copy the template:

```bash
cp .env.template .env
```

2. Fill in:

- source settings
- deployment target settings
- Solace broker settings
- Solace Event Portal settings
- optional AI settings

3. Set the execution mode in `.env`:

- `SOURCE_MODE=openapi` or `SOURCE_MODE=database`
- `DEPLOYMENT_TARGET=ec2` or `DEPLOYMENT_TARGET=kubernetes`

4. Point the agent at:

- `WORKSPACE_ROOT`
  - where it can generate or adapt the integration project
- `RUNS_ROOT`
  - where it can persist run artifacts
- `MDK_SAMPLE_ROOT`
  - where it can find the Solace MDK reference project or template baseline

5. Give the agent:

- this repository
- the filled `.env`
- the source contract or source credentials
- a target workspace where it can generate the integration

## Copy-Paste Agent Prompt

Use this as the starting prompt for a coding agent:

```text
Use this repository as your skill and execution guide.

Read SKILL.md first, then create_micro_integrations.md.
Load only the reference files you need for the current source and deployment target.

Use the provided .env values as the configuration contract.

Take over the full lifecycle of this micro-integration:
- discover the source
- design the canonical events
- reuse or generate the connector
- validate and test it
- deploy it to the target runtime
- register and link the resulting artifacts in Solace Event Portal
- write the operator and consumer documentation

Do not stop at code generation.
The run is only complete when runtime, governance, and documentation are all in place.
```

## Event Portal Completion Standard

The agent should not consider Event Portal complete when only the top-level Application exists.

A complete design model requires:

1. application domain
2. application
3. application version
4. schema
5. schema version
6. event
7. event version
8. application version produced-event links

This is what makes the application usable in Event Portal Designer.

## Expected Output From a Successful Run

- discovery summary
- integration design summary
- canonical topics, schemas, and application names
- generated project location
- validation and test evidence
- deployment target and runtime URL
- Event Portal registration and linking summary
- operator runbook
- known limitations and follow-up actions

## Recommended Usage Modes

### OpenAPI-first

Best for:

- REST ingress
- webhooks
- API to event conversion

Typical settings:

- `SOURCE_MODE=openapi`
- `SOURCE_OPENAPI_FILE=/absolute/path/to/spec.yaml`
- `DEPLOYMENT_TARGET=ec2` or `kubernetes`

### Database-first

Best for:

- table or entity discovery
- CDC or polling integrations
- turning operational data into governed event streams

Typical settings:

- `SOURCE_MODE=database`
- `SOURCE_DATABASE_KIND=postgres` or `mysql`
- `DEPLOYMENT_TARGET=ec2` or `kubernetes`

## Installing as a Codex Skill

To make this repo available as a local Codex skill:

```bash
cp -R /absolute/path/to/agentic-integration-factory "$CODEX_HOME/skills/agentic-integration-factory"
```

Then prompt Codex to use `agentic-integration-factory`.

## Repository Layout

```text
agentic-integration-factory/
  SKILL.md
  create_micro_integrations.md
  .env.template
  README.md
  LICENSE
  .gitignore
  agents/
    openai.yaml
  references/
    lifecycle_pipeline.md
    source_discovery.md
    deployment_and_ha.md
    event_portal_and_serdes.md
    event_portal_linking_playbook.md
```

## License

This repo includes a placeholder license file.

Recommendation:

- use `Apache-2.0` if you want broad reuse with explicit patent language
