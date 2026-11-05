# §4 Pace and scope

[← Playbook](../delivery-playbook.md)

## Daily rhythm

| Time | What |
|---|---|
| 08:30–08:50 | The SessionStart hook shows the phase. Pick today's A/B task and C task; Frame the A/B task |
| 08:50–09:00 | Kick off the C task in a worktree (`claude --worktree <name>`) |
| 09:00–12:00 | **Deep block**, mode A/B. Notifications off |
| 13:00–13:45 | Review the C task's diff, give feedback, queue its next step |
| 13:45–16:00 | Finish the A/B task: green, review, refactor, verify, PR |
| 16:00–17:00 | Read the deep-dive part of tomorrow's reference file |
| 17:00–17:15 | Journal: task entry, reflection, cut-list update |

**Fridays:** 09–11 game day ([§5](05-production-practice.md)) · 11–12 incident report · 13–14 weekly review · 14–15 interview drill ([§8](08-interview.md)) · 15–17 buffer.

## Stop rules

- Estimate every task in hours when you Frame it. Most are 2–6 h; none should be more than a day.
- At **1.5×** the estimate: stop and split off the smallest mergeable part.
- At **2×**: cut the rest to the Could list or park it, and write down why.
- Refactors and ideas outside the task go into the week file's **parking lot**, not into the PR.
- Blocked for more than an hour: have a subagent explore the blocker, switch tasks, come back after lunch.

## Must / Should / Could

Musts are what the interview stories and milestones depend on. **Cut from the bottom when you're behind.**

| Phase | Must | Should | Could (cut first) |
|---|---|---|---|
| P0 | T10, T11 | T13 | T12 |
| P1 | T01–T16 | T17 | — |
| P2 | T01–T08, **T09**, T10, **T13** | T12 | T11 |
| P3 | T01–T07, T10, T11, T16 | T13 (per-channel search), T14 | T08, T09, T12, T13 (global search), T15 |
| P4 | T01–T03, T05–T10, T13, T14, T16 | T04, T11, T12, T15 | T17 |
| P5 | T01–T07, T10 | T12, T13, T14 | T08, T09, T11, T15 |
| P6 | T01–T07, T09 | T08, T10, T11 | — |
| P7 | T01–T06, T08 (local) | T09, T11 | T07, T10 |
| P8 | T01–T05, T08–T10, T12–T14, T16, T19, T20 | T06, T17, T18 | T11, T15, T21–T23 |
| P9 | T01–T05, T08, T10, T11 | T06, T07, T09 | — |

P2-T09 and P2-T13 are Must because of the $20 budget; P2-T07 because P2-T04 needs its zone and cert (ADR-0013).

**Cut order when behind:** P5 Coulds → P3 Coulds → P8e (T21–T23) → P7 Grafana Cloud (T07, T10; keep local LGTM) → P4-T15/T17. Never cut tests, the leak test (P8-T10) or the journal.

## Milestones

| Id | Week | Demo | Your extra gate |
|---|---|---|---|
| M1 | 3 | `git push` → running on ECS behind CloudFront | First AWS window done and hibernated; real cost per hour measured |
| M2 | 6 | Two tasks, cross-task chat, survive a deploy | **Backend interview kit ready** |
| M3 | 8 | UI + Grafana dashboard | 6 game-day reports written |
| M4 | 11 | Ask DevCool with citations, no leaks | **AI interview kit ready** |
| M5 | 12 | Load-test numbers | `interview-stories.md` (P9-T11) built from the journal |

## Twelve weeks at a glance

Modes: **A** drive, **B** pair, **C** delegate. If P1 slips, everything moves by the same amount; protect the week-6 gate by cutting Coulds, not game days.

| Week | You drive (A/B) | Delegated (C) | Friday game day | Interview prep | AWS |
|---|---|---|---|---|---|
| 1 | P0, P1 (A) | — | — (set up the journal) | Pitch v0 | Budget alarm, ADR-0013 |
| 2 | P1 finish (A) | P2 Terraform | DTO leak, migration edit | — | Bootstrap only |
| 3 | P3 (A) | P2 finish; P7 local; P5 scaffold | Seq race, duplicate storm | STAR: BOLA fix | **M1** (~4 h) |
| 4 | P3 finish (A), P4 (B) | P5 | N+1 / slow query | STAR: idempotent send | — |
| 5 | P4 (B) | P5 | Kill a node; Valkey outage | STAR: Flyway baseline | — |
| 6 | P4 finish (B) | P5 | Drain; slow client | **Backend kit → apply** | **M2** (~6 h) |
| 7 | P6 (B) | P7; P5 | Worker stop; poison; duplicate | Outbox story | — |
| 8 | P8a (A) | P7, P5 finish | **First blind drill** | Interviews ongoing | **M3** (~2 h) |
| 9 | P8b (A) | P8 wiring | Leak attempts | RAG pitch v0 | — |
| 10 | P8c, P8d (A) | P8-T15 | Model latency; injection | STAR: leak test | — |
| 11 | P8d finish (A) | — | Re-index; eval regression | **AI kit ready** | **M4** (~3 h) |
| 12 | P9 (B/C) | README, load scripts | Full AWS chaos | `interview-stories.md` | **M5** (~8 h), then hibernate |
