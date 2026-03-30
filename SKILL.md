---
name: agentic-integration-factory
description: >
  Use when asked to build, deploy, govern, or operate a micro-integration end to end.
  Accepts any source — OpenAPI specs, databases (Postgres, MySQL, Oracle, MSSQL),
  SaaS webhooks, message queues, file drops, custom APIs — and produces a
  production-ready, event-driven connector running on Solace PubSub+ with full
  design-time governance in Solace Event Portal.
  Covers the complete lifecycle: discover, design, reuse, generate, validate,
  deploy, govern, document, and operate.
---

# Agentic Integration Factory

A complete agent skill for owning the micro-integration lifecycle from source discovery through production operations. The agent treats **Solace PubSub+** as the universal event transport and **Solace Event Portal** as the design-time governance surface, while remaining fully agnostic about what produces or consumes the events.

---

## Table of Contents

1. [Security Model](#1-security-model)
2. [Event Reuse Philosophy](#2-event-reuse-philosophy)
3. [Configuration Contract](#3-configuration-contract)
4. [Supported Sources](#4-supported-sources)
5. [Golden Lifecycle](#5-golden-lifecycle)
6. [Solace PubSub+ Broker Reference](#6-solace-pubsub-broker-reference)
7. [Solace Event Portal v2 API Reference](#7-solace-event-portal-v2-api-reference)
8. [Topic Taxonomy Standard](#8-topic-taxonomy-standard)
9. [Default Decision Rules](#9-default-decision-rules)
10. [Acceptance Standard](#10-acceptance-standard)

---

## 1. Security Model

Security is not a phase. It is a constraint on every phase.

### 1.1 Non-Negotiable Security Rules

- **Never log, print, embed, or commit secrets.** Not in generated code, not in docs, not in prompts, not in browser consoles, not in Event Portal descriptions.
- **Use read-only credentials for discovery.** Escalate to write credentials only at deploy time, after the user approves the deploy plan.
- **Do not mutate source systems during discovery.** Inspect only.
- **Encrypt in transit.** All broker connections must use `tcps://` (TLS) or `wss://` (Secure WebSocket) in production. Never `tcp://` or `ws://` outside of local development.
- **Inject secrets through the platform.** Use Kubernetes Secrets, AWS Secrets Manager, HashiCorp Vault, or environment variables mounted from a secure source. Never bake secrets into images, Dockerfiles, or Helm values files.
- **Scope credentials minimally.** Each integration gets its own client username, ACL profile, and client profile on the broker. No shared superuser accounts.
- **Validate TLS certificates.** Set `SSL_VALIDATE_CERTIFICATE=true` and `SSL_VALIDATE_CERTIFICATE_DATE=true` on all JCSMP connections. Do not disable certificate validation to "make it work."
- **Rotate secrets.** Document the rotation path for every credential the integration uses.

### 1.2 Broker Security Setup

For every new micro-integration, create dedicated broker objects:

**1. ACL Profile** — controls what the integration can publish/subscribe to:

```bash
# Create ACL profile with deny-by-default
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  "https://{broker}/SEMP/v2/config/msgVpns/{vpn}/aclProfiles" \
  -d '{
    "aclProfileName": "{integration-name}-acl",
    "clientConnectDefaultAction": "allow",
    "publishTopicDefaultAction": "disallow",
    "subscribeTopicDefaultAction": "disallow"
  }'

# Allow publishing only to this integration's topic namespace
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  "https://{broker}/SEMP/v2/config/msgVpns/{vpn}/aclProfiles/{integration-name}-acl/publishTopicExceptions" \
  -d '{
    "publishTopicExceptionSyntax": "smf",
    "publishTopicException": "{domain}/{entity}/>"
  }'

# Allow subscribing to reply topics (if request-reply is used)
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  "https://{broker}/SEMP/v2/config/msgVpns/{vpn}/aclProfiles/{integration-name}-acl/subscribeTopicExceptions" \
  -d '{
    "subscribeTopicExceptionSyntax": "smf",
    "subscribeTopicException": "#P2P/>"
  }'
```

**2. Client Profile** — controls resource limits:

```bash
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  "https://{broker}/SEMP/v2/config/msgVpns/{vpn}/clientProfiles" \
  -d '{
    "clientProfileName": "{integration-name}-profile",
    "allowGuaranteedMsgSendEnabled": true,
    "allowGuaranteedMsgReceiveEnabled": true,
    "allowGuaranteedEndpointCreateEnabled": false,
    "maxConnectionCountPerClientUsername": 10,
    "maxEgressFlowCount": 100,
    "maxIngressFlowCount": 100,
    "maxSubscriptionCount": 500,
    "rejectMsgToSenderOnNoSubscriptionMatchEnabled": true
  }'
```

**3. Client Username** — binds ACL + profile to an identity:

```bash
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  "https://{broker}/SEMP/v2/config/msgVpns/{vpn}/clientUsernames" \
  -d '{
    "clientUsername": "{integration-name}-user",
    "aclProfileName": "{integration-name}-acl",
    "clientProfileName": "{integration-name}-profile",
    "password": "{generated-strong-password}",
    "enabled": true
  }'
```

### 1.3 Authentication Hierarchy

Use the strongest auth the environment supports:

| Priority | Method | When |
|----------|--------|------|
| 1 | **Client certificate (mTLS)** | Production, regulated environments |
| 2 | **OAuth 2.0 / OIDC** | Cloud-native, centralized identity |
| 3 | **Username + password over TLS** | Standard deployments |
| 4 | **Basic auth (dev only)** | Local development only |

For client certificate auth in JCSMP:

```java
properties.setProperty(JCSMPProperties.HOST, "tcps://broker:55443");
properties.setProperty(JCSMPProperties.AUTHENTICATION_SCHEME,
    JCSMPProperties.AUTHENTICATION_SCHEME_CLIENT_CERTIFICATE);
properties.setProperty(JCSMPProperties.SSL_KEY_STORE, "/path/to/keystore.jks");
properties.setProperty(JCSMPProperties.SSL_KEY_STORE_PASSWORD, System.getenv("KEYSTORE_PASS"));
properties.setProperty(JCSMPProperties.SSL_TRUST_STORE, "/path/to/truststore.jks");
properties.setProperty(JCSMPProperties.SSL_TRUST_STORE_PASSWORD, System.getenv("TRUSTSTORE_PASS"));
properties.setProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, true);
properties.setProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE_DATE, true);
```

### 1.4 VPN Isolation

Use separate Message VPNs to isolate environments. **Never** encode environment names (`dev/`, `qa/`, `prod/`) in topic strings.

| Environment | VPN | Broker |
|-------------|-----|--------|
| Development | `dev-vpn` | Dev broker or shared broker |
| Staging | `staging-vpn` | Staging broker |
| Production | `prod-vpn` | Production HA broker pair |

---

## 2. Event Reuse Philosophy

### 2.1 Core Principle

**Design events for the organization, not for the integration.**

A well-designed event should be consumable by any future application without requiring changes to the producer. This means:

- Events represent **business facts**, not API responses or database rows.
- Schemas are **self-describing** and **versioned from day one**.
- Topics encode **routing semantics**, not implementation details.
- Events are **registered in Event Portal** so they are discoverable by any team.

### 2.2 Before Generating Anything, Search

The agent must search for reusable assets before creating new ones. Search order:

1. **Event Portal** — does an event with this business meaning already exist?
   ```
   GET /api/v2/architecture/events?name={event-name}&applicationDomainId={domain-id}
   ```
2. **Event Portal schemas** — does a schema for this entity already exist?
   ```
   GET /api/v2/architecture/schemas?name={schema-name}&applicationDomainId={domain-id}
   ```
3. **Prior generated runs** — check `RUNS_ROOT` for previous integrations in the same domain.
4. **Existing connectors** — check `WORKSPACE_ROOT` and `MDK_SAMPLE_ROOT` for reusable adapters.

### 2.3 Reuse Decision Matrix

| Situation | Action |
|-----------|--------|
| Exact event + schema exists in Event Portal, version is RELEASED | **Consume it.** Add a new consumer application version. Do not duplicate. |
| Event exists but schema needs extension | **Create a new schema version** (additive only). Create a new event version referencing it. |
| Similar event exists in a different domain | **Evaluate sharing.** If the event is genuinely cross-domain, mark it `shared: true` and reference it. Otherwise create a domain-local event. |
| No matching event exists | **Create new.** Follow the full registration order in Section 7. |
| Existing connector handles 80%+ of the mapping | **Adapt it.** Fork, extend, and attribute the original. |
| No reusable connector exists | **Generate new** from the MDK reference project. |

### 2.4 Schema Evolution Rules

- **Always additive.** New fields are optional with defaults.
- **Never remove or rename** a field in an existing version. Create a new version.
- **Version independently.** A schema version bump does not require an event version bump unless the topic structure changes.
- **Use JSON Schema `draft-07`** or later for all JSON payloads.
- **Include metadata fields** in every schema:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "OrderCreated",
  "required": ["eventId", "eventTimestamp", "source", "orderId"],
  "properties": {
    "eventId":        { "type": "string", "format": "uuid", "description": "Unique event identifier for idempotency" },
    "eventTimestamp": { "type": "string", "format": "date-time", "description": "ISO 8601 UTC timestamp of when the event occurred" },
    "source":         { "type": "string", "description": "Producing application name" },
    "correlationId":  { "type": "string", "description": "End-to-end correlation identifier" },
    "orderId":        { "type": "string" },
    "customerId":     { "type": "string" },
    "totalAmount":    { "type": "number" },
    "currency":       { "type": "string", "minLength": 3, "maxLength": 3 },
    "lineItems":      { "type": "array", "items": { "$ref": "#/$defs/LineItem" } }
  },
  "$defs": {
    "LineItem": {
      "type": "object",
      "required": ["sku", "quantity"],
      "properties": {
        "sku":      { "type": "string" },
        "quantity": { "type": "integer", "minimum": 1 },
        "price":    { "type": "number" }
      }
    }
  }
}
```

---

## 3. Configuration Contract

Copy `.env.template` to `.env` and fill in the values. The agent reads these to determine the source mode, deployment target, and all credentials.

### 3.1 Execution Mode

| Variable | Values | Purpose |
|----------|--------|---------|
| `SOURCE_MODE` | `openapi`, `database`, `webhook`, `file`, `queue`, `custom` | What kind of source to discover |
| `DEPLOYMENT_TARGET` | `ec2`, `kubernetes`, `docker`, `local` | Where to deploy the integration |
| `EVENT_TRANSPORT` | `solace` | Runtime event broker (always Solace) |
| `GOVERNANCE_TARGET` | `event_portal` | Design-time governance (always Event Portal) |

### 3.2 Workspace

| Variable | Default | Purpose |
|----------|---------|---------|
| `WORKSPACE_ROOT` | `./workspace` | Where the agent generates the integration project |
| `RUNS_ROOT` | `./generated-runs` | Where the agent persists run artifacts and audit trails |
| `MDK_SAMPLE_ROOT` | `./mdk-reference/micro-integration` | Solace MDK reference project or template baseline |

### 3.3 Solace PubSub+ Broker

| Variable | Purpose |
|----------|---------|
| `SOLACE_BROKER_URL` | SMF connection URL (`tcps://host:55443`) |
| `SOLACE_VPN` | Message VPN name |
| `SOLACE_USERNAME` | Client username |
| `SOLACE_PASSWORD` | Client password |
| `SOLACE_SEMP_URL` | SEMP management URL (`https://host:943`) |
| `SOLACE_SEMP_USERNAME` | SEMP admin username |
| `SOLACE_SEMP_PASSWORD` | SEMP admin password |
| `SOLACE_REST_URL` | REST messaging URL (`https://host:9443`) |
| `SOLACE_WEB_MESSAGING_URL` | WebSocket URL (`wss://host:1443`) |

### 3.4 Solace Event Portal

| Variable | Purpose |
|----------|---------|
| `EVENT_PORTAL_BASE_URL` | API base (`https://api.solace.cloud`) |
| `EVENT_PORTAL_TOKEN` | Bearer API token |
| `EVENT_PORTAL_ORG_ID` | Organization ID |
| `EVENT_PORTAL_DOMAIN_ID` | Pre-existing application domain ID (optional) |

Regional base URLs:

| Region | Base URL |
|--------|----------|
| US | `https://api.solace.cloud` |
| EU | `https://api.solacecloud.eu` |
| Australia | `https://api.solacecloud.com.au` |
| Singapore | `https://api.solacecloud.sg` |

### 3.5 Deployment Targets

**AWS / EC2:**

| Variable | Purpose |
|----------|---------|
| `AWS_REGION` | AWS region |
| `AWS_ACCESS_KEY_ID` | IAM access key |
| `AWS_SECRET_ACCESS_KEY` | IAM secret key |
| `AWS_SESSION_TOKEN` | Session token (optional) |
| `ECR_REPOSITORY_NAME` | ECR repository |
| `DEPLOY_EC2_HOST` | Direct EC2 host (optional) |
| `DEPLOY_EC2_SSH_USER` | SSH user (default `ec2-user`) |
| `DEPLOY_EC2_SSH_PRIVATE_KEY` | Path to SSH private key |

**Kubernetes / Rancher:**

| Variable | Purpose |
|----------|---------|
| `K8S_API_SERVER` | Cluster API server URL |
| `K8S_TOKEN` | Service account token |
| `K8S_NAMESPACE` | Target namespace |
| `K8S_CA_CERT` | CA certificate |
| `RANCHER_URL` | Rancher management URL (optional) |
| `RANCHER_TOKEN` | Rancher API token (optional) |

**Container Registry:**

| Variable | Purpose |
|----------|---------|
| `CONTAINER_REGISTRY` | Registry host |
| `CONTAINER_REGISTRY_USERNAME` | Registry username |
| `CONTAINER_REGISTRY_PASSWORD` | Registry password |
| `CONTAINER_IMAGE_PREFIX` | Image prefix (e.g. `ghcr.io/org/project`) |

### 3.6 Source-Specific Configuration

**OpenAPI:**

| Variable | Purpose |
|----------|---------|
| `SOURCE_OPENAPI_FILE` | Local path to the spec |
| `SOURCE_OPENAPI_URL` | URL to the spec |
| `SOURCE_API_BASE_URL` | Live API base URL |
| `SOURCE_API_KEY` | API key |
| `SOURCE_API_AUTH_TYPE` | Auth type (`bearer`, `basic`, `apikey`, `oauth2`) |

**Database (Postgres, MySQL, Oracle, MSSQL, or any JDBC-compatible):**

| Variable | Purpose |
|----------|---------|
| `SOURCE_DATABASE_KIND` | `postgres`, `mysql`, `oracle`, `mssql`, `jdbc` |
| `SOURCE_DATABASE_HOST` | Host |
| `SOURCE_DATABASE_PORT` | Port |
| `SOURCE_DATABASE_NAME` | Database name |
| `SOURCE_DATABASE_SCHEMA` | Schema name (default `public`) |
| `SOURCE_DATABASE_USERNAME` | Username |
| `SOURCE_DATABASE_PASSWORD` | Password |
| `SOURCE_DATABASE_SSLMODE` | `require`, `verify-ca`, `verify-full`, `disable` |

**Webhook / SaaS:**

| Variable | Purpose |
|----------|---------|
| `SOURCE_WEBHOOK_URL` | Incoming webhook endpoint |
| `SOURCE_WEBHOOK_SECRET` | HMAC signing secret |
| `SOURCE_SAAS_PROVIDER` | Provider name (`stripe`, `salesforce`, `shopify`, etc.) |
| `SOURCE_SAAS_API_KEY` | Provider API key |

### 3.7 Optional AI Refinement

| Variable | Purpose |
|----------|---------|
| `LITELLM_BASE_URL` | LiteLLM-compatible endpoint |
| `LITELLM_API_KEY` | API key |
| `LITELLM_MODEL` | Model identifier |

### 3.8 Optional Schema / SerDes Registry

| Variable | Purpose |
|----------|---------|
| `SERDES_REGISTRY_URL` | Registry URL |
| `SERDES_REGISTRY_TYPE` | `confluent`, `apicurio`, `glue`, `custom` |
| `SERDES_REGISTRY_API_KEY` | API key |

---

## 4. Supported Sources

The factory is source-agnostic. The lifecycle is the same regardless of what feeds the integration.

### 4.1 OpenAPI / REST API

**Discovery inspects:** title, version, servers, paths, operations, request/response schemas, auth schemes, tags, webhooks, pagination patterns, rate limits.

**Infers:** event-producing operations (POST/PUT/PATCH = state changes), canonical entities, sample payloads, candidate topics, idempotency keys from request IDs.

**Connector pattern:** HTTP ingress adapter that receives or polls the API, transforms payloads, and publishes to Solace topics.

### 4.2 Database (any JDBC-compatible)

**Discovery inspects:** database kind/version, schemas, tables, columns, primary keys, foreign keys, unique constraints, `created_at`/`updated_at` timestamps, soft-delete patterns (`deleted_at`, `is_active`), row counts, index cardinality, triggers, CDC capability (logical replication slots for Postgres, binlog for MySQL, LogMiner for Oracle, CT/CDC for MSSQL).

**Infers:** source entities, lifecycle transitions (created/updated/deleted), change capture strategy (CDC preferred, polling fallback), idempotency keys, event ordering by primary key or timestamp, relationship graph from foreign keys.

**Connector patterns:**
- **CDC** (preferred): Debezium or native logical replication, transforms change events, publishes to Solace.
- **Polling**: Periodic queries on `updated_at > last_checkpoint`, transforms rows, publishes to Solace.
- **Trigger-based**: Database triggers write to an outbox table, connector reads outbox and publishes.

### 4.3 Webhook / SaaS

**Discovery inspects:** provider documentation, webhook event catalog, payload schemas, retry policies, signature verification method (HMAC-SHA256, asymmetric), rate limits, historical event replay capability.

**Connector pattern:** HTTP endpoint receives webhooks, verifies signatures, transforms payloads, publishes to Solace. Must handle retries idempotently (deduplicate by event ID).

### 4.4 Message Queue (Kafka, RabbitMQ, JMS, AMQP, MQTT)

**Discovery inspects:** broker type, topics/queues/exchanges, message formats, serialization (Avro, Protobuf, JSON), consumer group state, offset/position, schema registry schemas.

**Connector pattern:** Consumer reads from source queue/topic, transforms, publishes to Solace. Must preserve ordering guarantees and handle rebalancing gracefully.

### 4.5 File / Object Store (S3, GCS, SFTP, local)

**Discovery inspects:** file patterns (CSV, JSON, Parquet, XML), directory structure, naming conventions, arrival frequency, file sizes, header/schema inference.

**Connector pattern:** File watcher or scheduled scan, parses files, extracts records, transforms, publishes individual events to Solace. Must track processed files to prevent re-processing.

### 4.6 Custom / Existing Connector

**Discovery inspects:** existing source code, dependencies, configuration, build system, tests, deployment artifacts. Assesses adapter quality, mapping completeness, error handling, and operational readiness.

**Decision:** Reuse as-is, adapt with minimal changes, or generate new.

---

## 5. Golden Lifecycle

### Stage 1 — Intake and Secret Binding

Bind all credentials server-side. Create an isolated workspace under `WORKSPACE_ROOT`. Create a run record under `RUNS_ROOT` with a unique run ID and timestamp.

**Security gate:** Verify that discovery credentials are read-only. Do not proceed with write-capable credentials until Stage 7 (Deploy).

**Outputs:** run record, isolated workspace, bound secret set.

### Stage 2 — Source Discovery

Inspect the source using the appropriate method from Section 4. Do not mutate the source.

**Guardrails:**
- Use read-only credentials.
- Do not guess schema semantics when the source can be inspected directly.
- Flag unclear ownership, missing timestamps, or poor CDC support as risks.
- Assess source connectivity, reliability, and throughput characteristics.

**Search for reuse** (Section 2.2) before proceeding to design.

**Outputs:** discovery report, connector reuse candidates, risk notes, proposed event set, proposed deployment pattern.

### Stage 3 — Canonical Event Modeling

Derive the canonical business entities and event model. This is the most important design step.

**Tasks:**
- Derive event candidates from source operations and entities.
- Name events as past-tense business facts: `OrderCreated`, `PaymentProcessed`, `ShipmentDispatched`.
- Define topic taxonomy following the standard in Section 8.
- Define JSON Schemas with the required metadata fields from Section 2.4.
- Define application names following `{domain}-{entity}-{connector-type}` convention.
- Set initial version to `0.1.0` for all new artifacts.
- Define producer/consumer ownership.

**Outputs:** canonical model (JSON or YAML), topic list, schema list, application name.

### Stage 4 — Generation

Generate or adapt the micro-integration.

**Tasks:**
- If reusing: fork the existing connector, apply adaptations, update config.
- If generating new: scaffold from `MDK_SAMPLE_ROOT`, render source adapters, mapping logic, Solace publishing, health endpoints, metrics, structured logging, Dockerfile, deployment manifests, tests, and documentation.
- Optionally run a bounded AI refinement pass after a valid baseline exists. AI may only modify code that compiles and passes tests. Reject AI patches that break the build.

**Outputs:** generated integration project, test fixtures, generated docs.

### Stage 5 — Validation

Fail closed on invalid code, broken mappings, missing config, or unverifiable output.

**Tasks:**
- Compile the code (`mvn compile` or equivalent).
- Run unit tests.
- Run contract tests against sample payloads.
- Run a happy-path publish test (if broker credentials are available).
- Verify that generated schemas match the canonical model.
- Verify that no secrets are embedded in generated code or docs.

**Outputs:** validation report, accepted artifact revision, runnable input fixtures.

### Stage 6 — Build and Package

**Tasks:**
- Build the container image: `docker build -t {image-prefix}/{integration-name}:{version} .`
- Push to the configured registry.
- Persist image tag, digest, and build log.
- Emit deployment metadata (Helm values, image reference, rollback coordinates).

**Outputs:** registry image reference, build log, deployment metadata.

### Stage 7 — Deploy and Harden

**Security gate:** This is the first stage that uses write credentials. Confirm the deploy plan with the user before proceeding.

**Broker provisioning** (via SEMP — see Section 6):
- Create the queue(s) with topic subscriptions.
- Create the ACL profile, client profile, and client username (Section 1.2).
- Verify broker connectivity from the deployment target.

**EC2 deployment** (demos, temporary environments):
- Deploy via SSH or CloudFormation.
- Set explicit TTL (`EPHEMERAL_EC2_TTL_MINUTES`).
- Verify health endpoint.

**Kubernetes deployment** (production):
- Apply Helm chart or manifests.
- HA minimum bar:
  - 2+ replicas
  - Readiness and liveness probes
  - Rolling deployment strategy
  - PodDisruptionBudget
  - Anti-affinity or topology spread
  - Secrets mounted from Kubernetes Secrets or external vault
  - Structured JSON logs
  - Prometheus metrics endpoint
  - HorizontalPodAutoscaler

**Operational hardening:**
- Retry with exponential backoff and jitter.
- Dead-letter queue (`DMQ`) for poison messages.
- Correlation IDs propagated end-to-end.
- Dashboards and alert thresholds.
- Rollback runbook.

**Outputs:** deployment metadata, runtime URL, rollout evidence.

### Stage 8 — Govern in Event Portal

Register or reconcile all design-time artifacts. Follow the exact order and API calls in Section 7.

**Outputs:** Event Portal sync report with all external IDs.

### Stage 9 — Live Verification

**Tasks:**
- Send a test payload through the integration.
- Verify the message arrives on the expected Solace topic.
- Verify schema compliance.
- Verify correlation ID propagation.
- If consumers exist, verify end-to-end flow.

**Verification via Solace REST API:**

```bash
# Publish a test message
curl -X POST \
  -u {username}:{password} \
  -H "Content-Type: application/json" \
  -H "Solace-Delivery-Mode: Persistent" \
  -H "Solace-Correlation-ID: test-$(date +%s)" \
  -d '{"eventId":"test-001","eventTimestamp":"2026-03-30T12:00:00Z","source":"integration-test","orderId":"TEST-001"}' \
  "https://{broker}:9443/TOPIC/{topic-path}"
```

**Outputs:** end-to-end verification record, correlation IDs.

### Stage 10 — Document and Hand Off

**Required documentation from every run:**

| Document | Audience | Contains |
|----------|----------|----------|
| Discovery summary | Engineers | Source analysis, risks, decisions |
| Integration design | Architects | Event model, topic taxonomy, schema catalog |
| Deployment runbook | Operators | How to deploy, scale, rollback, rotate secrets |
| Event catalog | Consumers | Event names, topics, schemas, sample payloads |
| Operational notes | SRE / Oncall | Health checks, alerts, dead-letter handling, known issues |
| Audit trail | Compliance | Run ID, timestamps, artifacts created, credentials used (names only) |

**Outputs:** all documentation files persisted under `RUNS_ROOT/{run-id}/docs/`.

### Stage 11 — Operate Continuously

**Tasks:**
- Monitor health, metrics, retries, dead-letter queue depth, and schema drift.
- Schedule periodic re-discovery to detect source changes.
- Propose safe updates when the source schema evolves.
- Re-run Event Portal reconciliation when new events or schema versions are added.

**Outputs:** operational handoff, maintenance plan.

---

## 6. Solace PubSub+ Broker Reference

### 6.1 SEMP v2 REST API (Management)

**Base URL:** `https://{broker}:{semp-port}/SEMP/v2/{api}/`

| API | Path Prefix | Purpose |
|-----|-------------|---------|
| Config | `/SEMP/v2/config/` | Read/write configuration |
| Monitor | `/SEMP/v2/monitor/` | Read operational state and stats |
| Action | `/SEMP/v2/action/` | Trigger operational actions (replay, etc.) |

**Authentication:** Basic Auth (`Authorization: Basic base64(user:pass)`).

**HTTP Methods:**

| Method | Behavior |
|--------|----------|
| `GET` | Read object or collection |
| `POST` | Create new object |
| `PUT` | Full replace (unset fields reset to default) |
| `PATCH` | Partial update (only specified fields change) |
| `DELETE` | Remove object |

### 6.2 Queue Management

**Create a queue:**

```bash
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  "https://{broker}/SEMP/v2/config/msgVpns/{vpn}/queues" \
  -d '{
    "queueName": "q/{domain}/{entity}/{action}",
    "accessType": "non-exclusive",
    "maxMsgSpoolUsage": 1000,
    "permission": "consume",
    "ingressEnabled": true,
    "egressEnabled": true,
    "respectMsgPriorityEnabled": false,
    "maxRedeliveryCount": 5,
    "rejectLowPriorityMsgEnabled": false,
    "deadMsgQueue": "DMQ/{domain}"
  }'
```

**Add topic subscription to the queue:**

```bash
curl -X POST -u admin:admin \
  -H "Content-Type: application/json" \
  "https://{broker}/SEMP/v2/config/msgVpns/{vpn}/queues/{queue}/subscriptions" \
  -d '{ "subscriptionTopic": "{domain}/{entity}/{action}/>" }'
```

**Check queue health:**

```bash
curl -u admin:admin \
  "https://{broker}/SEMP/v2/monitor/msgVpns/{vpn}/queues/{queue}?select=queueName,msgSpoolUsage,bindCount,msgVpnName"
```

### 6.3 REST Messaging (Publish/Consume)

**Publish to a topic:**

```bash
curl -X POST \
  -u {user}:{pass} \
  -H "Content-Type: application/json" \
  -H "Solace-Delivery-Mode: Persistent" \
  -H "Solace-Correlation-ID: {correlation-id}" \
  -H "Solace-User-Property-source: {integration-name}" \
  -d '{payload}' \
  "https://{broker}:9443/TOPIC/{topic-string}"
```

**Key Solace REST headers:**

| Header | Values | Purpose |
|--------|--------|---------|
| `Solace-Delivery-Mode` | `Direct`, `Non-Persistent`, `Persistent` | Message persistence |
| `Solace-Correlation-ID` | string | End-to-end correlation |
| `Solace-Time-To-Live-In-ms` | milliseconds | Message expiry |
| `Solace-DMQ-Eligible` | `true`/`false` | Route to dead-letter on expiry |
| `Solace-Reply-Wait-Time-In-ms` | milliseconds | Blocking request-reply |
| `Solace-User-Property-{name}` | `value[; type=<type>]` | Custom user properties |

### 6.4 JCSMP Connection Pattern (Java)

```java
JCSMPProperties props = new JCSMPProperties();
props.setProperty(JCSMPProperties.HOST, System.getenv("SOLACE_BROKER_URL"));
props.setProperty(JCSMPProperties.VPN_NAME, System.getenv("SOLACE_VPN"));
props.setProperty(JCSMPProperties.USERNAME, System.getenv("SOLACE_USERNAME"));
props.setProperty(JCSMPProperties.PASSWORD, System.getenv("SOLACE_PASSWORD"));

// Reconnection
JCSMPChannelProperties channelProps = new JCSMPChannelProperties();
channelProps.setReconnectRetries(20);
channelProps.setConnectRetriesPerHost(5);
channelProps.setReconnectRetryWaitInMillis(3000);
props.setProperty(JCSMPProperties.CLIENT_CHANNEL_PROPERTIES, channelProps);

// Guaranteed messaging window
props.setProperty(JCSMPProperties.PUB_ACK_WINDOW_SIZE, 50);

JCSMPSession session = JCSMPFactory.onlyInstance().createSession(props);
session.connect();
```

**Publish persistent message to topic:**

```java
XMLMessageProducer producer = session.getMessageProducer(correlatingEventHandler);
BytesMessage msg = JCSMPFactory.onlyInstance().createMessage(BytesMessage.class);
msg.setDeliveryMode(DeliveryMode.PERSISTENT);
msg.setCorrelationId(correlationId);
msg.setData(jsonPayload.getBytes(StandardCharsets.UTF_8));
msg.setHTTPContentType("application/json");

Topic topic = JCSMPFactory.onlyInstance().createTopic(topicString);
producer.send(msg, topic);
```

**Consume from queue with client ACK:**

```java
Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
flowProps.setEndpoint(queue);
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

FlowReceiver flow = session.createFlow(messageListener, flowProps, endpointProps);
flow.start();

// In the listener:
// msg.ackMessage();  -- only after successful processing
```

### 6.5 Spring Boot Configuration

```yaml
solace:
  java:
    host: ${SOLACE_BROKER_URL}
    msg-vpn: ${SOLACE_VPN}
    client-username: ${SOLACE_USERNAME}
    client-password: ${SOLACE_PASSWORD}
    connect-retries: 5
    reconnect-retries: 20
    reconnect-retry-wait-in-millis: 3000
```

---

## 7. Solace Event Portal v2 API Reference

**Base URL:** `{EVENT_PORTAL_BASE_URL}/api/v2/architecture`

**Authentication:** `Authorization: Bearer {EVENT_PORTAL_TOKEN}`

**Content-Type:** `application/json;charset=UTF-8`

### 7.1 Response Envelope

Single object: `{ "data": { ... }, "meta": {} }`

Collection: `{ "data": [ ... ], "meta": { "pagination": { "pageNumber": 1, "count": 5, "pageSize": 20, "nextPage": 2, "totalPages": 3 } } }`

**Pagination:** `?pageSize=20&pageNumber=1` (max `pageSize` is 100).

### 7.2 Version State Machine

| stateId | Name | Editable | Transitions To |
|---------|------|----------|----------------|
| `"1"` | DRAFT | Yes | RELEASED |
| `"2"` | RELEASED | No (immutable) | DEPRECATED |
| `"3"` | DEPRECATED | No | RETIRED |
| `"4"` | RETIRED | No | (terminal) |

Change state: `PATCH /api/v2/architecture/{type}Versions/{id}/state` with body `{ "stateId": "{id}" }`

### 7.3 Complete Registration Order

Build the design graph in this exact sequence. Each step depends on IDs from previous steps.

#### Step 1 — Reconcile Application Domain

```
GET  /api/v2/architecture/applicationDomains?name={exact-name}
POST /api/v2/architecture/applicationDomains
```

**Create payload:**
```json
{
  "name": "Acme Retail",
  "description": "Events for the Acme Retail domain",
  "uniqueTopicAddressEnforcementEnabled": true,
  "topicDomainEnforcementEnabled": false
}
```

Save: `applicationDomainId`

#### Step 2 — Reconcile Application

```
GET  /api/v2/architecture/applications?name={exact-name}&applicationDomainId={domainId}
POST /api/v2/architecture/applications
```

**Create payload:**
```json
{
  "name": "acme-orders-rest-ingest",
  "applicationDomainId": "{applicationDomainId}",
  "applicationType": "standard",
  "brokerType": "solace"
}
```

Save: `applicationId`

#### Step 3 — Reconcile Application Version

```
GET  /api/v2/architecture/applicationVersions?applicationIds={applicationId}
POST /api/v2/architecture/applicationVersions
```

**Create payload:**
```json
{
  "applicationId": "{applicationId}",
  "version": "0.1.0",
  "displayName": "v0.1.0",
  "description": "Initial version — REST ingress for Acme order events"
}
```

Save: `applicationVersionId`

#### Step 4 — Reconcile Schema(s)

```
GET  /api/v2/architecture/schemas?name={schema-name}&applicationDomainId={domainId}
POST /api/v2/architecture/schemas
```

**Create payload:**
```json
{
  "name": "OrderCreatedPayload",
  "applicationDomainId": "{applicationDomainId}",
  "schemaType": "jsonSchema",
  "shared": false
}
```

Save: `schemaId`

#### Step 5 — Reconcile Schema Version(s) with Real Content

```
GET  /api/v2/architecture/schemaVersions?schemaIds={schemaId}
POST /api/v2/architecture/schemaVersions
```

**Create payload:**
```json
{
  "schemaId": "{schemaId}",
  "version": "0.1.0",
  "displayName": "v0.1.0",
  "description": "OrderCreated event payload schema",
  "content": "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"type\":\"object\",\"title\":\"OrderCreated\",\"required\":[\"eventId\",\"eventTimestamp\",\"source\",\"orderId\"],\"properties\":{\"eventId\":{\"type\":\"string\",\"format\":\"uuid\"},\"eventTimestamp\":{\"type\":\"string\",\"format\":\"date-time\"},\"source\":{\"type\":\"string\"},\"orderId\":{\"type\":\"string\"},\"totalAmount\":{\"type\":\"number\"}}}"
}
```

**The `content` field must be a JSON string** — stringify the schema document.

Save: `schemaVersionId`

#### Step 6 — Reconcile Event(s)

```
GET  /api/v2/architecture/events?name={event-name}&applicationDomainId={domainId}
POST /api/v2/architecture/events
```

**Create payload:**
```json
{
  "name": "OrderCreated",
  "applicationDomainId": "{applicationDomainId}",
  "shared": false
}
```

Save: `eventId`

#### Step 7 — Reconcile Event Version(s) with Topic and Schema Binding

```
GET  /api/v2/architecture/eventVersions?eventIds={eventId}
POST /api/v2/architecture/eventVersions
```

**Create payload:**
```json
{
  "eventId": "{eventId}",
  "version": "0.1.0",
  "displayName": "v0.1.0",
  "description": "Order created event — published when a new order is placed",
  "schemaVersionId": "{schemaVersionId}",
  "deliveryDescriptor": {
    "brokerType": "solace",
    "address": {
      "addressType": "topic",
      "addressLevels": [
        { "name": "acmeretail",  "addressLevelType": "literal" },
        { "name": "orders",      "addressLevelType": "literal" },
        { "name": "order",       "addressLevelType": "literal" },
        { "name": "created",     "addressLevelType": "literal" },
        { "name": "v1",          "addressLevelType": "literal" },
        { "name": "region",      "addressLevelType": "variable" },
        { "name": "orderId",     "addressLevelType": "variable" }
      ]
    }
  }
}
```

This produces the topic pattern: `acmeretail/orders/order/created/v1/{region}/{orderId}`

Save: `eventVersionId`

#### Step 8 — Link Application Version to Produced Events

```
PATCH /api/v2/architecture/applicationVersions/{applicationVersionId}
```

**Patch payload:**
```json
{
  "declaredProducedEventVersionIds": ["{eventVersionId1}", "{eventVersionId2}"]
}
```

**This step is what makes the Application show a meaningful event flow in Event Portal Designer.** If this step is missing, the application appears empty.

**PATCH replaces the entire array.** Always send the complete list, not a delta.

### 7.4 Reconcile Rules

- **Lookup before create.** Always GET with exact name filter first.
- **Paginate deterministically.** Some filters are weak — if you get more results than expected, match by exact name in code.
- **Prefer idempotent upsert.** If the artifact exists, PATCH to update. If it doesn't, POST to create.
- **Capture every external ID.** Persist `applicationDomainId`, `applicationId`, `applicationVersionId`, `schemaId`, `schemaVersionId`, `eventId`, `eventVersionId` in the run record.
- **Never create placeholders.** Schema versions must contain real schema content. Event versions must contain real delivery descriptors.

### 7.5 Completion Standard

The Event Portal sync is complete **only** when:

- [ ] Application domain exists
- [ ] Application exists in the domain
- [ ] Application version exists with a description
- [ ] Every canonical schema has a schema + schema version with real content
- [ ] Every canonical event has an event + event version with delivery descriptor and schema version reference
- [ ] Application version `declaredProducedEventVersionIds` contains all produced event version IDs
- [ ] Topic addresses in Event Portal match the runtime topic taxonomy exactly

### 7.6 Common API Errors

| Code | Cause | Fix |
|------|-------|-----|
| 400 `"Not unique"` | Name already exists in scope | GET first, then reuse or version |
| 400 `version format` | Version not semver | Use `X.Y.Z` format |
| 401 | Invalid or expired token | Refresh token in Solace Cloud console |
| 404 | Wrong ID or deleted artifact | Re-query by name |
| 409 | Conflicting state transition | Check current state, only transition forward |

### 7.7 AsyncAPI Export

After building the design graph, export as AsyncAPI:

```
GET /api/v2/architecture/applicationVersions/{applicationVersionId}/exports/asyncApi
```

This produces an AsyncAPI specification document that can be shared with consumers.

---

## 8. Topic Taxonomy Standard

### 8.1 Format

```
{domain}/{noun}/{entity}/{verb}/{version}/{key1}/{key2}/...
```

| Level | Purpose | Example |
|-------|---------|---------|
| Domain | Business domain or bounded context | `acmeretail`, `logistics`, `payments` |
| Noun | Aggregate or resource group | `orders`, `shipments`, `invoices` |
| Entity | Specific entity type | `order`, `shipment`, `invoice` |
| Verb | Past-tense state change | `created`, `updated`, `cancelled`, `shipped` |
| Version | Schema/event version | `v1`, `v2` |
| Keys | Routing properties, least to most specific | `{region}`, `{customerId}`, `{orderId}` |

### 8.2 Examples

```
acmeretail/orders/order/created/v1/us-east/ORD-12345
acmeretail/orders/order/updated/v1/eu-west/ORD-67890
logistics/shipments/shipment/dispatched/v1/us-east/SHP-001
payments/invoices/invoice/paid/v1/us-east/INV-999
```

### 8.3 Wildcard Subscriptions

| Pattern | Matches |
|---------|---------|
| `acmeretail/orders/order/created/v1/>` | All v1 order-created events, any region and ID |
| `acmeretail/orders/order/*/v1/>` | All v1 order events (created, updated, cancelled, etc.) |
| `acmeretail/orders/>` | All order-related events in the acmeretail domain |
| `*/orders/order/created/>` | Order-created events across all domains |

- `*` matches exactly **one** topic level.
- `>` matches **one or more** remaining levels (must be the last token).

### 8.4 Queue Subscription Mapping

```
Queue: q/acmeretail/order-processor
  Subscriptions:
    acmeretail/orders/order/created/v1/>
    acmeretail/orders/order/updated/v1/>

Queue: q/analytics/all-orders
  Subscriptions:
    acmeretail/orders/>

Queue: DMQ/acmeretail
  (dead-letter queue for the domain)
```

### 8.5 Anti-Patterns

| Do Not | Instead |
|--------|---------|
| `dev/orders/created` | Use separate VPNs per environment |
| `orders_created_v1` | Use `/` hierarchy: `orders/order/created/v1` |
| Embed trace IDs in topics | Use message headers (`Solace-Correlation-ID`) |
| Use spaces or special characters | Use camelCase or kebab-case |
| Put high-cardinality keys first | Put them last (enables efficient wildcard subscriptions) |

---

## 9. Default Decision Rules

| Decision | Default | Override When |
|----------|---------|---------------|
| Code generation vs. AI | **Deterministic generation first** | AI only for bounded refinement after baseline passes tests |
| New event vs. reuse | **Reuse existing** | No matching event in Event Portal or prior runs |
| New connector vs. reuse | **Reuse existing** | No matching connector, or adaptation cost > 50% of new build |
| Deployment target | **Kubernetes (HA)** | EC2 for demos, local for development |
| Schema format | **JSON Schema draft-07** | Avro/Protobuf when consumers require it |
| Schema evolution | **Additive only** | Breaking change = new event version, new topic path |
| Event Portal sync | **Reconcile (idempotent)** | Blind create only on first run in a clean domain |
| Version baseline | **`0.1.0`** | User specifies otherwise |
| Message delivery | **Persistent (guaranteed)** | Direct only for ephemeral/low-value data |
| Lifecycle completeness | **Not done until all 11 stages pass** | User explicitly scopes a partial run |

---

## 10. Acceptance Standard

The run is complete **only** when the agent can demonstrate:

| Criterion | Evidence |
|-----------|----------|
| Source understood | Discovery report with entities, risks, and decisions |
| Events designed for reuse | Schemas with metadata fields, versioned, registered in Event Portal |
| Integration generated | Compilable project with tests, Dockerfile, and deployment manifests |
| Tests passed | Unit, contract, and happy-path test output |
| Deployment live | Runtime URL, health check passing, image tag recorded |
| Events flowing | Message on Solace topic verified with correlation ID |
| Event Portal complete | Full design graph linked (Section 7.5 checklist) |
| Security hardened | Dedicated ACL profile, client profile, TLS, no embedded secrets |
| Documentation written | Operator runbook, event catalog, consumer guide, audit trail |
| Operations handoff | Monitoring, alerting, dead-letter handling, maintenance plan |

---

## MDK Reference

Place the Solace MDK reference project or template baseline at `MDK_SAMPLE_ROOT`:

```
mdk-reference/
  micro-integration/
    pom.xml
    src/
      main/java/...
      test/java/...
    Dockerfile
    helm/
    README.md
```
