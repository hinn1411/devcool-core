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

- Feature work comes from a task id in `docs/plans/phases/phase-*.md` (e.g. `P3-T04`). Use `/implement-task <id>`.
- Respect the decisions in `docs/plans/architecture/adr/`. If a change contradicts an ADR, stop and say so instead of silently diverging.
- Tick the task checkbox in the phase file in the same change that implements it.
- Before finishing, run `/verify` (or the commands below that match the changed paths).
- Check library APIs with context7 before adding or upgrading a dependency.

## Commands

```bash
# Run application (local profile with Docker Compose for PostgreSQL)
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"

# Build (skip tests)
./mvnw clean package -DskipTests

# Run all tests (unit + integration)
./mvnw verify

# Run unit tests only
./mvnw test

# Run integration tests only
./mvnw -Dit verify

# Format code (Google Java Format via Spotless)
./mvnw spotless:apply

# Check formatting
./mvnw spotless:check

# Static analysis (Checkstyle, PMD, SpotBugs)
./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify
```

## Architecture

**Hexagonal Architecture (Ports & Adapters)**, one deployable. It will run as two ECS services (`api`, `worker`) from the same image, selected by Spring profile (ADR-0001).

```
com.devcool/
├── domain/          # Core business logic — no framework dependencies
│   ├── auth/        # JWT tokens, refresh tokens
│   ├── user/        # User, Role, UserStatus
│   ├── channel/     # Channel types (Forum, Lounge, PrivateChat)
│   ├── chat/        # Messages
│   ├── member/      # Channel membership
│   ├── media/       # Media upload (S3)
│   └── common/
├── application/
│   └── service/     # Use case implementations wiring domain ports
├── adapters/
│   ├── in/
│   │   ├── web/     # REST controllers + DTOs (MapStruct mappers)
│   │   └── websocket/ # WebSocket handlers + auth interceptor
│   └── out/
│       ├── persistence/ # JPA entities + Spring Data repositories
│       ├── storage/     # S3StorageAdapter (AWS SDK v2)
│       ├── jwt/         # JWT issuance/validation (nimbus-jose-jwt)
│       └── crypto/      # Password hashing
```

- Each domain defines **inbound ports** (`UseCase` / `Query`, called by `adapters/in`) and **outbound ports** (`Port`, implemented by `adapters/out`).
- Application services implement inbound ports and depend only on outbound port interfaces.
- A full worked example of one request crossing every layer is in `docs/architecture-request-flow.md`. Read it when adding a new endpoint.
- Detailed, path-scoped conventions load automatically from `.claude/rules/` (hexagonal, testing, Flyway, WebSocket protocol, GenAI safety).

### Key Technologies

| Concern | Technology |
|---|---|
| DB | PostgreSQL (Docker Compose locally; Aurora PostgreSQL in AWS) |
| ORM | Spring Data JPA + Flyway migrations |
| Auth | JWT (nimbus-jose-jwt) + refresh token cookie |
| Storage | AWS S3 (SDK v2, region: `ap-southeast-1`) |
| Code gen | Lombok + MapStruct 1.5.5 |
| Docs | Swagger UI at `/docs` |

### Media Upload Flow

- `POST /api/v1/medias/upload` — multipart file (JPEG, PNG, WebP, MP4; max 10MB)
- S3 key format: `channel/{channelId}/{date}/{uuid}.{ext}`
- `GET /api/v1/medias/presigned-url` — 10-minute presigned URL

### WebSocket

- Raw WebSocket (not STOMP) with a custom message protocol. Protocol v2 is specified in ADR-0005.
- Auth via `WsAuthHandShakeInterceptor`
- Message types today: Subscribe, Unsubscribe, SendMessage

### Profiles

| Profile | DDL | DB |
|---|---|---|
| `local` | `update` today → Flyway + `validate` after P1-T01 | Docker Compose PostgreSQL |
| `ecs` | `validate` (Flyway runs as a separate migrate task, P2) | RDS/Aurora via env vars |

Local env vars (JWT secrets etc.) are in `local.env`. Never read or print it.

### PR Review Guidelines

When reviewing a pull request, check for:

- **Hexagonal boundaries**: domain classes must not import Spring/JPA annotations; adapters must not contain business logic
- **Port contracts**: new use cases must define an inbound port interface in `domain/*/port/in/`; new external dependencies must define an outbound port interface in `domain/*/port/out/`
- **Application service rules**: services implement inbound ports and depend only on outbound port interfaces — never directly on JPA repositories or AWS SDK
- **DTO mapping**: HTTP request/response types stay in `adapters/in/web/dto/`; MapStruct mappers convert them to/from domain objects
- **Domain exceptions**: thrown from the application service layer, not from controllers or adapters
- **Authorization**: every read/write of channel data checks membership or role in the service layer (authenticated is not authorized)
- **Test coverage**: new service logic should have unit tests using Mockito (`@ExtendWith(MockitoExtension.class)`); new adapter logic should mock the underlying SDK/JPA calls
- **Code formatting**: must pass `./mvnw spotless:check` (Google Java Format)

### CI Pipeline (`.github/workflows/ci.yml`)

1. Spotless format check
2. Static analysis (Checkstyle / PMD / SpotBugs via `static-analysis` profile). It is currently non-blocking (`|| true`); P1-T14 makes it blocking.
3. Build + unit tests. Integration tests are currently skipped (`-DskipITs`); P1-T14 enables them.
4. JaCoCo coverage report + optional SonarQube
