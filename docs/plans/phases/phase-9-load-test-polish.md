# Phase 9 — Load test, chaos and polish

**Week:** 12 · **Depends on:** everything · **ADRs:** all

## Goal
Numbers you can quote, failure behaviour you've seen with your own eyes, and a repo that explains itself in five minutes.

## Why it matters (interview angle)
"How many concurrent connections does one task handle?" — "About N at p99 delivery of X ms; the limit was Y, found with k6." Measured numbers beat estimates in every interview.

## Scope
- **In:** k6 load tests, chaos tests, tuning, the README and architecture diagram, a cost report, CV bullets, and an interview story bank.
- **Out:** new features.

## Tasks
### Load
- [ ] **P9-T01** k6 scripts in `loadtest/`:
  - (a) Connection soak: ramp to N sockets with heartbeats only.
  - (b) Chat mix: 5% of users send 1 msg/10 s into channels of 2–10 members.
  - (c) Reconnect storm: drop all sockets at once.
  - (d) REST history paging.
  Tickets come from a setup phase.
- [ ] **P9-T02** Measure per task size (0.5 vCPU/1 GB, 1 vCPU/2 GB): max sockets at p99 delivery < 300 ms, CPU/heap, `ws.send.overflow`. Record in `docs/plans/load-test-report.md`
- [ ] **P9-T03** Tune from the data: task size, autoscaling targets, Hikari pool, the decorator limits, ALB/ECS timeouts. Re-run the tests and add a before/after table
### Chaos
- [ ] **P9-T04** Kill one `api` task during the chat mix: measure the reconnect time and verify zero lost messages (compare client seq logs to the DB)
- [ ] **P9-T05** Stop Valkey access (security group change in dev) for 60 s: local delivery continues and cross-task delivery recovers via resume. Document it
- [ ] **P9-T06** Stop the worker for 5 min: the outbox lag alert fires, the backlog drains after restart, and there are no duplicates in effect
- [ ] **P9-T07** Bedrock failure (deny IAM temporarily): the circuit opens and the fallback is served
### Polish
- [ ] **P9-T08** Root `README.md`:
  - Architecture diagram and feature list.
  - "Run locally in one command" (compose profiles).
  - Links to the ADRs.
  - A demo GIF.
- [ ] **P9-T09** Security pass with the `security-reviewer` agent and the `security-guidance` plugin. Optional WAF managed rules on CloudFront
- [ ] **P9-T10** Cost report: the monthly cost per component in `awake` vs `sleep` vs `hibernate`, from Cost Explorer
- [ ] **P9-T11** `docs/plans/interview-stories.md`:
  - 8–10 STAR stories: the seq race, the leak test, the drain, the outbox duplicate, a load-test finding, a production-ish incident.
  - CV bullets with numbers.
  - Generate the drafts with `/interview-prep` on each phase file, then edit them.

## Definition of Done
Milestone **M5**: the load test report has real numbers and the chaos results are documented. The README lets a reviewer understand and run the system, and the interview stories are written.

## Interview talking points
- How you found the bottleneck (it's rarely where you expect: send buffers, the connection pool, GC).
- What "zero lost messages" means and how you proved it.
- The cost of idle vs active, and the design choices that made scale-to-zero possible.
