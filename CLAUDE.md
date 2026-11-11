# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository map (monorepo)

| Path | What | Own instructions |
|---|---|---|
| `/` (`pom.xml`, `src/`) | Spring Boot 3.5.6 / Java 21 backend, hexagonal | this file + `.claude/rules/` |
| `frontend/` | React + Vite + TypeScript SPA | `frontend/CLAUDE.md` |
| `infra/` | Terraform (AWS: ECS Fargate, Aurora, Valkey, SNS/SQS, CloudFront) | `infra/CLAUDE.md` |
| `docker/local/` | Docker Compose for local dependencies | — |
| `docs/plans/` | **Roadmap, architecture, ADRs, phase sub-plans.** Start here for any feature work | — |
| `docs/learning/`, `docs/improvements/` | Audit notes and review lessons | — |

## Workflow

- Feature work comes from a task id in `docs/plans/phases/phase-*.md` (e.g. `P3-T04`). Plan it first, then build it test-first:
  1. **Plan (`/frame-task <id>`):** no code. Covers why, value and core concepts; the user predicts the requirements, compares them, and settles the open decisions → agreed brief in `docs/journal/briefs/<id>.md`. Its **Done when** list drives the tests.
  2. **Red (`/implement-task <id>` starts here):** one failing test per brief **Done when** item (+ negative authz). Tests must compile and fail on an assertion. Commit brief + tests (`test(<id>): …`).
  3. **Green:** the simplest code that passes every test, without touching the Red tests. Commit (`feat(<id>): …`).
  4. **Review:** reviewer agents picked by changed paths → one ranked list; the user approves or rejects each item.
  5. **Refactor:** apply the approved items plus cleanups, keep the tests green, then the full unit + IT suite and SpotBugs (see Commands).
  6. **Close:** tick the task in the phase file in the final commit (`refactor(<id>): …`).

  Never push before Green: a Red commit fails CI.
- Respect the decisions in `docs/plans/architecture/adr/`. If a change contradicts an ADR, stop and say so instead of silently diverging.
- Local test scope: Red/Green run only the **affected tests** (see Commands). Close runs the full unit + IT suite once (`./mvnw -q -Dit verify`) and SpotBugs (`-Pstatic-analysis`), not the full build: the rest runs in CI. `/verify` stays the opt-in full local check.
- Check library APIs with context7 before adding or upgrading a dependency.

## Commands

```bash
# Run application (local profile with Docker Compose for PostgreSQL)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Run all tests (unit + integration)
./mvnw verify

# Run integration tests only
./mvnw -Dit verify

# Static analysis (SpotBugs; EI_EXPOSE_REP* baseline in spotbugs-exclude.xml)
./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify

# Affected unit tests only (comma-separated classes)
./mvnw -q test -Dtest='FooTest,BarTest' -Dsurefire.failIfNoSpecifiedTests=false

# Affected ITs only (skips unit tests and the JaCoCo floor: a partial run can't meet it)
./mvnw -q -Dit verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test='FooIT' -Djacoco.skip=true
```

**Affected tests** = changed `*Test`/`*IT` files + `<Class>Test`/`<Class>IT` for each changed main class + test classes that reference a changed main class (`grep -rlw <Class> src/test`). Run ITs only when a migration, `persistence`, `websocket`, `messaging`, `web/controller` or `config` path changed.

## Architecture

**Hexagonal Architecture (Ports & Adapters)**, one deployable. It will run as two ECS services (`api`, `worker`) from the same image, selected by Spring profile (ADR-0001).

- Each domain defines **inbound ports** (`UseCase` / `Query`, called by `adapters/in`) and **outbound ports** (`Port`, implemented by `adapters/out`).
- Application services implement inbound ports and depend only on outbound port interfaces.
- A full worked example of one request crossing every layer is in `docs/architecture-request-flow.md`. Read it when adding a new endpoint.
- Detailed, path-scoped conventions load automatically from `.claude/rules/` (hexagonal, testing, Flyway, WebSocket protocol, GenAI safety).

### WebSocket

- Raw WebSocket (not STOMP) with a custom message protocol. Protocol v2 is specified in ADR-0005.

### Profiles

| Profile | DDL | DB |
|---|---|---|
| `local` | Flyway migrates on start + `validate` | Docker Compose PostgreSQL |
| `ecs` | `validate`; Flyway off in the app | RDS/Aurora via env vars |
| `ecs,migrate` | One-off task before each deploy: Flyway + `validate`, no web server, exits | same as `ecs` |

Local env vars (JWT secrets etc.) are in `local.env`. Never read or print it.

### CI and build guards

CI conventions (the `ci.yml` router, `ci-gate`, the composite actions) are in `.github/CLAUDE.md`, which loads when you work under `.github/`. These rules apply everywhere:

- Keep `infra-plan.yml`, `infra-apply.yml`, `deploy.yml` and `env-power.yml` as workflow files with those names: the OIDC roles trust them by file name (`infra/stacks/bootstrap/oidc.tf`).
- The JaCoCo line-coverage floor (`jacoco.line.minimum` in `pom.xml`): raise it, never lower it.
- SpotBugs findings that existed at P1-T14 are baselined in `spotbugs-exclude.xml`. Only remove entries; fix new findings instead of adding them.
