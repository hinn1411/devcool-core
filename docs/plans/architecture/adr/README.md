# Architecture Decision Records

An ADR records one decision: the context, the options considered, what was chosen and what it costs. They are short on purpose. When a decision changes, write a new ADR that supersedes the old one. Don't rewrite history.

Create a new one with `/write-adr <title>`.

## Index

| # | Decision | Status |
|---|---|---|
| [0001](0001-modular-monolith-hexagonal.md) | Hexagonal modular monolith, deployed as `api` + `worker` from one image | Accepted |
| [0002](0002-ecs-fargate.md) | ECS Fargate for compute | Accepted |
| [0003](0003-terraform-layered-stacks.md) | Terraform with layered stacks and per-env tfvars | Accepted |
| [0004](0004-monorepo.md) | Monorepo: backend at root, `frontend/`, `infra/` | Accepted |
| [0005](0005-raw-websocket-protocol-v2.md) | Keep raw WebSocket; typed protocol v2 envelope | Accepted |
| [0006](0006-redis-pubsub-backplane.md) | Valkey/Redis pub/sub backplane with per-channel topics | Accepted |
| [0007](0007-event-backbone-outbox-sns-sqs.md) | Transactional outbox → SNS → SQS for async events (**full options comparison**) | Accepted |
| [0008](0008-observability-otel-grafana-cloud.md) | OpenTelemetry → Grafana Cloud via a collector sidecar (**full options comparison**) | Accepted |
| [0009](0009-aurora-postgres-pgvector.md) | Aurora Serverless v2 PostgreSQL with pgvector | Accepted |
| [0010](0010-bedrock-spring-ai.md) | Amazon Bedrock through Spring AI, behind ports | Accepted |
| [0011](0011-ws-ticket-auth-single-origin.md) | One-time WS tickets and a single CloudFront origin | Accepted |
| [0012](0012-message-ordering-per-channel-seq.md) | Per-channel sequence numbers for ordering and gap detection | Accepted |

## Template

```markdown
# NNNN — Title

- **Status:** Proposed | Accepted | Superseded by NNNN
- **Date:** YYYY-MM-DD
- **Phase:** Pn

## Context
What forces are at play? What problem needs a decision? Link code and docs.

## Decision drivers
- Bullet list of what matters most (e.g. scale-to-zero, ops burden, interview value).

## Options considered
### Option A — name
Pros / Cons.
### Option B — name
Pros / Cons.

## Decision
What we chose, in one or two sentences.

## Consequences
What becomes easier, what becomes harder, what we must now do.

## Revisit when
The observable trigger that should reopen this decision.
```
