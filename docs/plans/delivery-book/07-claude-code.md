# §7 Claude Code: faster without losing quality

[← Playbook](../delivery-playbook.md)

How the mechanisms work is in the [Claude Code guide](../claude-code/claude-code-guide.md). This page covers how to use them on this schedule.

## Where speed comes from, and the catch

- **Parallelism:** one C task in a worktree while you drive an A/B task. More than one means hurried reviews.
- **Front-loaded thinking:** Frame before code. Skipping it "because it's small" is how you merge code you can't explain.
- **Cheap verification:** hooks, `/verify` and reviewer agents. Green checks prove nothing if the tests are weak; that's what break-it is for.
- **Clean context:** one task per session, `/clear` between tasks, `/rewind` instead of arguing with a wrong turn.
- **Right effort:** high for concurrency, SQL and security; lower for DTOs, mappers and Terraform variables.

## Quality gates per mode (non-negotiable)

| Gate | A | B | C |
|---|---|---|---|
| Tests from the phase's Test plan exist | ✓ | ✓ | ✓ |
| You wrote the first failing test | ✓ | ✓ | — |
| Break-it check done | ✓ | ✓ | — |
| `/verify` green (no skipped group reported as passing) | ✓ | ✓ | ✓ |
| `hexagonal-reviewer` (backend); `security-reviewer` for auth, WS, AI, infra | ✓ | ✓ | ✓ |
| You read every line of the diff | ✓ | Core; rest skimmed | Public surface, tests, `terraform plan` |
| Merge gate (3 questions) | ✓ | ✓ | ✓ at behaviour level |
| Explain-back (Self-check) | ✓ | ✓ | One paragraph in the journal |

## Anti-patterns

| Anti-pattern | Instead |
|---|---|
| Claude "fixes" a failing test by weakening it | Diff the test files first in every review |
| The IT mocks the thing it should test | Concurrency, SQL and Redis are tested on Testcontainers, never mocks |
| "Fixed" without a red test | Reproduce with a failing test first, always |
| Claude silently diverges from an ADR | `/implement-task` stops on conflicts; you decide, then `/write-adr` |
| Big PRs ("do the whole phase") | One task per PR; split at 1.5× |
| Letting Claude choose the design in mode A | Learning style and `TODO(human)`; your Frame comes first |
| Accepting an explanation you can't reproduce | Explain-back without notes; misses go on Friday's list |

## Measure your own delivery

Each task entry records: estimate, actual, mode, correction rounds, CI failures, reviewer findings, and what Claude got wrong. The weekly review looks at tasks done vs planned, average overrun, cuts, and the "Claude got wrong" log. These numbers are your answer to "how do you use AI tools?"
