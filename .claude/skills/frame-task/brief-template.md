# <task-id> — <short title>

| Phase | Mode | Tier | Estimate | Status | Date |
|---|---|---|---|---|---|
| Pn — <phase name> | A / B / C | Must / Should / Could | <h> | draft / agreed | YYYY-MM-DD |

**In one sentence:** what this task delivers.

## Why
- **The problem today:** what the code does now, with evidence (`path:line`, a `docs/learning` finding, or an ADR).
- **Without this task:** the concrete failure, risk or blocker. Who or what hits it, and when.

## Value to the goal
- **Chain:** this task → the phase goal → milestone (M1–M5) → the roadmap target. Show it as one line with arrows.
- **Unblocks:** downstream task ids that need this one.
- **What you get out of it:** the knowledge, production experience or interview story it builds (playbook §0 goals).

## Core concepts
Repeat this block for each concept (2–5).

### <Concept>
- **What:** a one-line definition.
- **Without:** a DevCool scenario, step by step, ending in the failure.
- **With:** the same scenario, with the concept applied, ending in the correct outcome.
- **In DevCool:** where it lives (ADR, design section, class or table).
- **Read:** one source from `docs/references/NN-*.md`.

## Requirements
- **In scope:** …
- **Out of scope:** … (and which task or phase owns it)
- **Acceptance criteria:**
  1. Given … when … then …
- **Invariants:** what must never be violated, including under concurrency, retries or partial failure.
- **Authorization and non-functional:** who may do this (a membership or role check in the service), plus performance, limits and errors (status codes).
- **Tests:**
  - [ ] unit: …
  - [ ] IT: …
  - [ ] negative authz: …
- **Likely files:** …
- **Depends on / blocked by:** …

## Decisions
| Question | Answer | Source (ADR, doc or user) |
|---|---|---|

## Prediction vs brief
| Your prediction | Brief | Tag (knowledge gap / plan issue / open) | Follow-up |
|---|---|---|---|

## Self-check (answer after implementing, before merging)
1. What does it do? (two sentences, no code)
2. Why this way, and which option wasn't taken?
3. How does it fail, and how would you know? (test, metric, log)
