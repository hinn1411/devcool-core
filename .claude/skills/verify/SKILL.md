---
name: verify
description: Run the checks that match what changed on this branch (backend, frontend, infra) and report a pass/fail table. Use before opening a PR or when asked to verify.
disable-model-invocation: true
---

# Verify the current branch

Changed files vs `origin/master` (committed + working tree):
!`git diff --name-only origin/master...HEAD`
!`git status --short`

Run only the groups whose paths changed. Run them in the order shown and stop a group at its first failure. Fix what's clearly caused by this branch; report anything else.

| Group | Trigger paths | Commands |
|---|---|---|
| Backend format | `src/**`, `pom.xml` | `./mvnw -q spotless:check` (if it fails: `./mvnw -q spotless:apply`, then re-check) |
| Backend unit | `src/**`, `pom.xml` | `./mvnw -q test` |
| Backend integration | `src/main/resources/db/migration/**`, `**/persistence/**`, `**/websocket/**`, `**/messaging/**`, `**/web/controller/**`, `**/config/**`, `pom.xml` | `./mvnw -q -Dit verify` (needs Docker) |
| Backend static analysis | `src/main/**` | `./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify` |
| Frontend | `frontend/**` | from `frontend/`: `npm run lint`, `npm run typecheck`, `npm run test -- --run`, `npm run build` |
| Infra | `infra/**` | `terraform fmt -check -recursive infra`; for each changed stack: `terraform -chdir=infra/stacks/<s> init -backend=false` then `validate`; `tflint --chdir infra` if installed |
| Docs | `docs/**` | Check that relative links in changed markdown files resolve |

Report as a table: group · result · the key error lines (≤ 5 each). Finish with one line saying whether the branch is ready for a PR. Never mark a skipped group as passing; say it was skipped and why (e.g. Docker not running).
