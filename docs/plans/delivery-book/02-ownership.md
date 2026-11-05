# §2 Ownership: who writes what

[← Playbook](../delivery-playbook.md)

| Mode | You | Claude | Output style |
|---|---|---|---|
| **A. Drive** | Write the core logic, SQL and the first test for each behaviour | Explores, scaffolds the boring parts, reviews, extends tests | **Learning**: design decisions come to you as `TODO(human)` |
| **B. Pair** | Design first (invariants, sequence diagram, test list), then write the hard core | Writes the rest from your design; reviews your core | **Explanatory** |
| **C. Delegate + operate** | Frame, review the plan or diff, run the operations yourself (apply, deploy, sleep/wake, drills) | Writes it, usually in a worktree | Default |

Switch styles with `/output-style`.

| Area | Phases | Mode | What "you write" means here |
|---|---|---|---|
| Backend core | P1, P3 | **A** | Authorization checks, DTO boundaries, Flyway baseline, seq assignment and idempotent send (P3-T03), edit/delete rules, history queries, read state |
| GenAI core | P8a–d | **A** | `ChunkBuilder` (P8-T02), retrieval SQL and permission filter (P8-T09), the leak test (P8-T10), prompt and citation validation (P8-T13), the eval harness (P8-T19) |
| GenAI wiring | P8 | C | Spring AI adapters (P8-T03, T12), Terraform (P8-T07), the frontend panel (P8-T15) |
| Realtime | P4 | **B** | Ref-counted subscribe (P4-T08), `RESUME` (P4-T09), presence (P4-T10), drain (P4-T14). Claude writes envelopes, dispatcher, typing, metrics |
| Async events | P6 | **B** | The `SKIP LOCKED` relay (P6-T05) and the idempotent consumer base (P6-T07). Claude writes Terraform, LocalStack wiring, housekeeping |
| Infra, CI/CD, observability | P2, P7 | C | Nothing by hand. You read every `terraform plan`, run every apply and power change, and write the runbooks' "why" |
| Frontend | P5 | C | Nothing by hand. You review behaviour and the WS client state machine (P5-T06) |

**Tests.** In modes A and B you write the first failing test for each behaviour: choosing the assertion *is* understanding the behaviour. Claude adds edge cases; you check that each new test can fail.
