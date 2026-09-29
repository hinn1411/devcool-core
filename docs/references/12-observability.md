# 12 — Observability: OpenTelemetry, Grafana, SLOs

> **Used in DevCool:** [ADR-0008](../plans/architecture/adr/0008-observability-otel-grafana-cloud.md) · [Phase 7](../plans/phases/phase-7-observability.md) (P7-T01–T11) · P6-T09 (trace propagation through SNS/SQS) · P4-T15, P6-T05, P8-T18 (metrics) · P1-T15 (structured logs)
> **Links checked:** 2026-09-29

## Concepts to own

- **Three signals, correlated.** Metrics tell you *that* something is wrong, traces *where*, logs *why*. Correlation: `trace_id` in logs, exemplars on metric histograms linking to traces.
- **OpenTelemetry pieces.** API/SDK in the app, the Java agent for auto-instrumentation, OTLP as the wire protocol, the Collector as a pipeline (receivers → processors → exporters), semantic conventions for attribute names.
- **Resources.** `service.name`, `service.version`, `deployment.environment` identify where telemetry came from; `resourcedetection` adds ECS task metadata.
- **Context propagation.** W3C `traceparent` carries trace id + span id across HTTP automatically. Across SNS/SQS you inject and extract it yourself, and the consumer span *links* to the producer rather than being its child.
- **Manual spans for WebSocket frames.** A frame isn't an HTTP request, so no auto-instrumentation: one span per inbound frame type.
- **Head vs tail sampling.** Head sampling decides at the start (cheap, blind); tail sampling decides after the trace completes in the Collector (keep errors, slow traces, and a percentage).
- **Cardinality.** Every unique label combination is a new time series. `userId`/`channelId`/`messageId` go on spans and logs, never on metric labels.
- **Histograms and percentiles.** Averages hide the tail; p99 from histograms (aggregatable) rather than client-side summaries (not aggregatable across tasks).
- **RED and USE.** RED (Rate, Errors, Duration) for request-driven services; USE (Utilization, Saturation, Errors) for resources (CPU, pool, queue).
- **SLOs and burn-rate alerts.** An SLO (99.5% of messages delivered < 300 ms) defines an error budget; multi-window burn-rate alerts page on budget consumption speed, not on raw thresholds.
- **Sidecar Collector.** Batching, retry and credentials off the app heap; `memory_limiter` protects the task.

## Read first

1. [OpenTelemetry — Concepts](https://opentelemetry.io/docs/concepts/) — *official docs* · Signals, context propagation, resources, sampling. Read "Traces" including span links.
2. [OpenTelemetry Java agent](https://opentelemetry.io/docs/zero-code/java/agent/) — *official docs* · What the `-javaagent` instruments, configuration via env vars, and how to add manual spans next to it.
3. [OpenTelemetry Collector — Configuration](https://opentelemetry.io/docs/collector/configuration/) — *official docs* · Receivers, processors, exporters, pipelines: the shape of `infra/observability/collector.yaml`.
4. [The RED Method: how to instrument your services](https://grafana.com/blog/the-red-method-how-to-instrument-your-services/) — *Tom Wilkie, Grafana blog* · The dashboard layout in P7-T08.
5. [Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/) — *Google SRE Workbook* · Why multi-window, multi-burn-rate alerts; the recipe for P7-T09.

## Reference

### OpenTelemetry

- [Context propagation](https://opentelemetry.io/docs/concepts/context-propagation/) — *official docs* · Propagators and carriers; what you implement for SNS message attributes.
- [Sampling](https://opentelemetry.io/docs/concepts/sampling/) — *official docs* · Head vs tail, and the cost trade-off.
- [Java agent configuration](https://opentelemetry.io/docs/languages/java/configuration/) — *official docs* · `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_RESOURCE_ATTRIBUTES`, sampler settings.
- [Supported libraries (Java instrumentation)](https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md) — *GitHub* · Check Spring MVC, JDBC, Lettuce, AWS SDK v2 coverage.
- [W3C Trace Context](https://www.w3.org/TR/trace-context/) — *W3C Recommendation* · The `traceparent` / `tracestate` format.
- [Semantic conventions — messaging spans](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/) — *spec* · Span kinds, names and links for publish/receive/process; model the WS frame spans on these.
- [GenAI semantic conventions — metrics](https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/gen-ai-metrics.md) — *spec, now in its own repo* · `gen_ai.client.token.usage` and `gen_ai.client.operation.duration` used in P8-T18. See also the [AWS Bedrock conventions](https://github.com/open-telemetry/semantic-conventions-genai/blob/main/docs/gen-ai/aws-bedrock.md).
- [Inside the LLM Call: GenAI Observability with OpenTelemetry](https://opentelemetry.io/blog/2026/genai-observability/) — *OpenTelemetry blog* · How the GenAI spans and metrics fit together in practice.

### Collector components (DevCool's pipeline)

- [Collector deployment patterns](https://opentelemetry.io/docs/collector/deploy/) — *official docs* · Agent (sidecar) vs gateway; ADR-0008 Part 3.
- [memory_limiter processor](https://github.com/open-telemetry/opentelemetry-collector/blob/main/processor/memorylimiterprocessor/README.md) — *README* · Must come first in the pipeline.
- [batch processor](https://github.com/open-telemetry/opentelemetry-collector/blob/main/processor/batchprocessor/README.md) — *README*.
- [resourcedetection processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/resourcedetectionprocessor/README.md) — *README* · The `ecs` detector.
- [tail_sampling processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/processor/tailsamplingprocessor/README.md) — *README* · Policies: status code, latency, probabilistic, composite.
- [otlphttp exporter](https://github.com/open-telemetry/opentelemetry-collector/blob/main/exporter/otlphttpexporter/README.md) — *README* · Endpoint + auth headers for Grafana Cloud.

### Metrics in Spring

- [Micrometer documentation](https://docs.micrometer.io/micrometer/reference/) — *official docs* · Counters, timers, gauges, histograms, and the naming convention.
- [Micrometer OTLP registry](https://docs.micrometer.io/micrometer/reference/implementations/otlp.html) — *official docs* · Exporting Micrometer metrics over OTLP.
- [Spring Boot — Observability](https://docs.spring.io/spring-boot/3.5/reference/actuator/observability.html) and [Metrics](https://docs.spring.io/spring-boot/3.5/reference/actuator/metrics.html) — *official docs* · The Observation API and built-in metrics (Hikari, JVM, Tomcat).
- [Prometheus — Histograms and summaries](https://prometheus.io/docs/practices/histograms/) — *official docs* · Why histograms aggregate across tasks and summaries don't.
- [Prometheus — Instrumentation: do not overuse labels](https://prometheus.io/docs/practices/instrumentation/#do-not-overuse-labels) — *official docs* · Cardinality in one paragraph.

### Grafana stack

- [Send data using OpenTelemetry Protocol (Grafana Cloud)](https://grafana.com/docs/grafana-cloud/send-data/otlp/send-data-otlp/) — *official docs* · The OTLP gateway endpoint and auth.
- [grafana/docker-otel-lgtm](https://github.com/grafana/docker-otel-lgtm) — *README* · The one-container local stack (`--profile o11y`).
- [PromQL basics](https://prometheus.io/docs/prometheus/latest/querying/basics/) — *official docs*; [LogQL](https://grafana.com/docs/loki/latest/query/) and [TraceQL](https://grafana.com/docs/tempo/latest/traceql/) — *Grafana docs* · The three query languages for the runbook in P7-T11.
- [Exemplars](https://grafana.com/docs/grafana/latest/fundamentals/exemplars/) — *Grafana docs* · Jump from a latency spike to a trace.
- [Amazon ECS FireLens](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/using_firelens.html) — *AWS docs* · One of the log-routing options P7-T03 has to decide between.
- [AWS Distro for OpenTelemetry](https://aws-otel.github.io/docs/introduction) — *AWS* · Option B in ADR-0008: same instrumentation, AWS-managed backends.

### SRE foundations

- [Monitoring Distributed Systems](https://sre.google/sre-book/monitoring-distributed-systems/) — *Google SRE Book, ch. 6* · The four golden signals; symptoms vs causes.
- [Service Level Objectives](https://sre.google/sre-book/service-level-objectives/) — *Google SRE Book, ch. 4* · SLIs, SLOs, error budgets.
- [Implementing SLOs](https://sre.google/workbook/implementing-slos/) — *Google SRE Workbook* · Choosing SLIs for a real service.
- [The USE Method](https://www.brendangregg.com/usemethod.html) — *Brendan Gregg* · Resource-oriented checklist for the JVM/DB dashboard.

### Books

- *Observability Engineering* (Majors, Fong-Jones, Miranda) — events vs metrics, high cardinality, SLO-based alerting.
- *Site Reliability Engineering* and *The Site Reliability Workbook* (Google) — free online at sre.google.
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- Why is `channelId` a span attribute and not a metric label? Estimate the series count if it were a label.
- An edit flows api → DB → relay → SNS → SQS → indexer → Bedrock. Where does the trace context break, and how do you carry it? Link or child span?
- Head vs tail sampling: which one lets you keep 100% of error traces, and what does it cost?
- Why report p99 from histograms instead of averaging per-task p99s?
- Define an SLI and SLO for message delivery. What does a 14.4× burn rate over 1 h mean?
- RED vs USE: which applies to the WS handler, and which to the Hikari pool?
- Why put the Collector in a sidecar instead of exporting straight from the agent?
