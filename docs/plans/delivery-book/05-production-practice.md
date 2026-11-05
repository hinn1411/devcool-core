# §5 Production practice without real users

[← Playbook](../delivery-playbook.md)

Real users bring traffic, surprises and pressure. Simulate all three, and never claim you had users ([honesty rules](08-interview.md#honesty-rules)).

## Local production-like stack

Two `api` instances behind a WebSocket-capable load balancer, one `worker`, `pgvector/pgvector:pg16`, `valkey/valkey`, LocalStack (SNS/SQS) and `grafana/otel-lgtm`: the P4-T16 multi-node setup, kept running and observable.

> **Extra task E1 (proposed, mode C):** a `prodlike` compose profile as above, after P4-T08. Add it to the phase file if you adopt it.

## Weekly game days

Run each scenario as soon as the feature it breaks exists, using one script:

1. **Hypothesis**, e.g. "If Valkey is down for 60 s, same-task delivery continues, cross-task stops, and every client catches up via `RESUME` within 30 s."
2. **Steady state:** the normal metric (delivery p99, `ws.connections.active`, `outbox.lag.seconds`).
3. **Inject** the fault at a noted time.
4. **Detect:** how you noticed, and the time to detect (TTD).
5. **Recover:** what you did, and the time to recover (TTR).
6. **Write up** an [incident report](09-journal-templates.md#incident-report) in `docs/journal/incidents/`.

| Week | Scenario | Built by | Proves |
|---|---|---|---|
| 2 | A DTO regression leaks `password`/`tokenVersion`; an edit to a merged migration | P1-T04, P0 hook | The IT and the hook catch it before merge |
| 3 | 50 concurrent sends to one channel; a duplicate `clientMsgId` storm | P3-T03 | No gaps, no duplicates, one seq bump per message |
| 4 | Slow history page: find the N+1 or missing index | P1-T03, P3-T05 | Diagnosis with `EXPLAIN` and query counts |
| 5 | Kill `api-2` mid-chat; stop Valkey for 60 s | P4-T08, T09 | Resume works; health stays green |
| 6 | Deploy during chat; a client that stops reading | P4-T14, T01 | Reconnect spread; only the slow client is cut |
| 7 | Stop the worker 5 min; a poison message; a duplicate SNS delivery | P6-T05, T07, T11 | Lag alert fires; DLQ + redrive; one effect |
| 8 | **Blind drill** using any of the above | E2 | You detect from alerts, not from knowing |
| 9 | Non-member exact-match query; member removed mid-session | P8-T10 | Zero leakage |
| 10 | Model latency and throttling; a prompt-injection message | P8-T16, T20 | Circuit opens, fallback served; injection ignored |
| 11 | Embedding model change → re-index; an eval regression | P8-T06, T19 | Stale rows fixed; the eval catches it |
| 12 | Kill a task, cut Valkey, stop the worker, deny Bedrock, on AWS | P9-T04–T07 | Same results on real infrastructure |

## Blind drills (from week 8)

> **Extra task E2 (proposed, mode C):** a chaos script picks a random fault from the table, runs it at a random time in the next two hours, and writes what it did to a gitignored sealed file you open only after your write-up.

Route local Grafana alerts to your phone so an alert interrupts you like a page. Keep the runbooks (P6-T11, P7-T11) open and note where they were wrong.

## Synthetic users and deploys

- **Traffic:** a k6 "chat mix" (P9-T01, brought forward) or a bot client runs during game days, so dashboards and alerts have real data.
- **Deploys:** every AWS window starts with a real deploy through `deploy.yml`. At least once, deploy a broken image and watch the circuit breaker roll it back.

**Keep as evidence:** incident reports, before/after numbers, dashboard screenshots (in the journal), the load-test report (P9-T02), corrected runbooks, the monthly AWS bill.
