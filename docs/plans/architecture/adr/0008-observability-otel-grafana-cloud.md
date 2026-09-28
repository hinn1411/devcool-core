# 0008 — Observability: OpenTelemetry → Grafana Cloud via a collector sidecar

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P7 (local stack from P1/P3)

## Context
There is no observability beyond console logs. A distributed chat system with a backplane, an async pipeline and an LLM dependency is undebuggable without all three signals:
- **Metrics:** WS connections, delivery latency, outbox lag, queue age, token usage.
- **Logs:** structured, correlated with traces.
- **Traces:** a message edit flows api → DB → relay → SNS → SQS → indexer → Bedrock.

The user wants "the o11y stack wired" regardless of cost, but idle cost still matters.

## Decision drivers
1. All three signals, correlated (trace id in logs, exemplars on metrics).
2. Vendor-neutral instrumentation: changing backend must not mean re-instrumenting.
3. Same experience locally and in AWS.
4. Low or no ops; low idle cost.
5. Interview value (OpenTelemetry, SLOs, RED/USE).

## Part 1 — Instrumentation: OpenTelemetry

| | **OpenTelemetry** (chosen) | Micrometer only + vendor agents |
|---|---|---|
| Traces | OTel Java agent auto-instruments Spring MVC, JDBC, Lettuce, AWS SDK, HTTP clients | Micrometer Tracing (Brave/OTel bridge) needs more wiring |
| Metrics | Micrometer stays the API in code (Spring's native choice) and is exported through OTLP | Vendor-specific registries |
| Logs | Logback appender or stdout JSON with `trace_id`/`span_id` | Vendor log shipper |
| Portability | Any OTLP backend | Tied to the vendor |

Decision: the **OTel Java agent** (`-javaagent`) for auto-instrumentation. Micrometer for custom metrics (`MeterRegistry`), bridged to OTLP. Spring Boot structured logging (`logging.structured.format.console=ecs`) with trace context in stdout JSON.

## Part 2 — Backend options

### Option A — Grafana Cloud (Mimir + Loki + Tempo + Grafana) (**chosen**)
- **Pros:**
  - Managed LGTM stack; nothing to run.
  - A **free tier** that fits a portfolio project (limits on active series, log GB and trace GB per month).
  - Native OTLP endpoint.
  - The same dashboards and queries (PromQL, LogQL, TraceQL) you'd use self-hosted.
  - SLO and alerting built in.
  - An AWS CloudWatch integration pulls ALB/ECS/Aurora/SQS metrics without an agent.
  - **Local parity:** the `grafana/otel-lgtm` image is the same stack in one container.
- **Cons:**
  - Data leaves AWS (egress via NAT; privacy review for real products).
  - Free-tier limits force cardinality discipline (good habit, but real).
  - Another account to manage.

### Option B — AWS managed: ADOT → Amazon Managed Prometheus + Amazon Managed Grafana + X-Ray + CloudWatch Logs
- **Pros:**
  - Stays inside AWS (IAM auth, VPC endpoints, no NAT egress for telemetry).
  - The ADOT collector is an AWS-supported OTel distribution.
  - X-Ray integrates with SNS/SQS/Lambda consoles.
  - Good for AWS certification practice.
- **Cons:**
  - Four services, four pricing models.
  - AMG is billed per active user per month.
  - Logs in CloudWatch, traces in X-Ray and metrics in AMP make correlation clunkier than one Grafana stack.
  - No free local equivalent (you'd still run LGTM locally).

### Option C — Self-hosted LGTM on ECS (Prometheus/Mimir, Loki, Tempo, Grafana as services)
- **Pros:**
  - Full control; the most learning about how the stack works.
  - No per-series pricing.
- **Cons:**
  - Stateful services on ECS (EFS or S3 backends), upgrades, retention, auth, backups.
  - Always-on tasks (no scale to zero).
  - Monitoring your monitoring.
  - Weeks of work that isn't the product.

### Option D — CloudWatch only (Logs, Metrics, Container Insights, Application Signals)
- **Pros:**
  - Zero setup for ECS logs and basic metrics.
  - Application Signals gives some APM out of the box.
  - Native alarms feed autoscaling.
- **Cons:**
  - Weak ad-hoc querying and dashboards compared with Grafana.
  - Custom metric pricing adds up.
  - Trace/log/metric correlation is limited.
  - AWS-only skills.

### Option E — Commercial APM (Datadog, New Relic, Honeycomb)
- **Pros:**
  - Best UX.
  - Powerful trace analytics (Honeycomb) and one-click integrations.
- **Cons:**
  - Cost scales steeply after trials.
  - Vendor agents or pricing encourage lock-in.
  - Free tiers are narrow.

### Comparison

| Criterion | **A Grafana Cloud** | B AWS managed | C Self-hosted | D CloudWatch | E Commercial |
|---|---|---|---|---|---|
| Metrics / logs / traces | ✓ / ✓ / ✓ | ✓ / ✓ / ✓ (3 products) | ✓ / ✓ / ✓ | ✓ / ✓ / partial | ✓ / ✓ / ✓ |
| Correlation (trace ↔ log ↔ metric) | strong | medium | strong | weak–medium | strong |
| OTel-native | ✓ | ✓ (ADOT) | ✓ | partial | ✓ |
| Ops burden | none | low | **high** | none | none |
| Idle cost | free tier | AMG per user + minimums | always-on tasks | low | trial, then high |
| Local parity | `grafana/otel-lgtm` | ✗ | same stack | ✗ | ✗ |
| Data stays in AWS | ✗ | ✓ | ✓ | ✓ | ✗ |
| Interview value | high (LGTM, OTel, SLOs) | high for AWS roles | high but costly | medium | medium |

## Part 3 — Export topology

| | App → backend directly (agent exports OTLP) | **Collector sidecar per task** (chosen) | Central collector service |
|---|---|---|---|
| Pros | Fewest moving parts | Batching, retry and queueing off the app's heap. Tail sampling. Resource detection (ECS metadata). Credentials live in the collector, not the app. Swap backend by config | One place to configure; fewer containers |
| Cons | Credentials and retry logic in every app. Harder to add processing | One more container per task (~64–128 MB) | A network hop; its own scaling and availability |

## Decision
- The **OTel Java agent** in the image, exporting OTLP to `localhost:4317`.
- An **OTel Collector (contrib) sidecar** in each ECS task (`api` and `worker`). Config comes from SSM Parameter Store.
- Pipeline: `otlp receiver → memory_limiter → resourcedetection(ecs) → batch → [tail_sampling for traces] → otlphttp exporter` to the Grafana Cloud OTLP gateway, with basic auth from Secrets Manager.
- **Grafana Cloud AWS integration** (CloudWatch metrics) for ALB, ECS, Aurora, ElastiCache and SQS.
- **Locally:** `grafana/otel-lgtm` in `docker/local/compose.yaml`, with the same agent config pointed at it.

## Consequences
- Keep metric label cardinality low: no `userId`, `channelId` or `messageId` labels. Put those on spans and logs.
- Trace context must cross the async boundary by hand: inject `traceparent` into SNS message attributes; extract it in SQS listeners (the agent covers the AWS SDK calls; verify the span links).
- WebSocket frames are not HTTP requests: create manual spans per inbound frame type (`ws.frame SEND`) with `messaging.*`-style attributes.
- Dashboards and alert rules are stored as JSON/YAML in `infra/observability/`, not only clicked together.

## Revisit when
- Free-tier limits are hit consistently → reduce cardinality first, then consider Option B (AWS credits) or a paid tier.
- Compliance requires telemetry to stay in AWS → Option B, the same instrumentation with a different collector exporter.
