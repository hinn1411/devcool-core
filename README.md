# DevCool

A real-time chat and collaboration platform backend built with **Spring Boot 3.5 / Java 21**, designed around **Hexagonal Architecture** and deployed on AWS.

## Table of Contents

- [Introduction](#introduction)
- [Tech Stack](#tech-stack)
- [Hexagonal Architecture](#hexagonal-architecture)
- [AWS Architecture](#aws-architecture)
- [Local Development](#local-development)
- [Running Tests](#running-tests)
- [API Docs](#api-docs)

---

## Introduction

DevCool is a backend API service that powers a developer-focused chat platform. It supports:

- **Authentication** — JWT-based auth with access tokens and HttpOnly refresh token cookies
- **Channels** — Forum, Lounge, and Private Chat channel types with membership management
- **Real-time messaging** — Raw WebSocket connections with custom message protocol (Subscribe / Unsubscribe / SendMessage)
- **Media uploads** — Multipart file upload to S3 (JPEG, PNG, WebP, MP4; max 10 MB) with presigned URL retrieval

The codebase enforces strict layering so that domain logic never depends on Spring, JPA, or AWS — making it easy to test in isolation and swap infrastructure adapters without touching business rules.

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.6 |
| Database | PostgreSQL 16 (Aurora Serverless v2 on AWS) |
| ORM / Migrations | Spring Data JPA + Flyway |
| Auth | JWT (nimbus-jose-jwt) |
| Storage | AWS S3 (SDK v2, region: `us-east-1`) |
| WebSocket | Spring WebSocket (raw, not STOMP) |
| Code generation | Lombok + MapStruct 1.5.5 |
| API Docs | SpringDoc OpenAPI (Swagger UI at `/docs`) |
| Build | Maven 3.9+ |
| Code quality | Spotless (Google Java Format), SpotBugs, ArchUnit |
| Test coverage | JaCoCo + optional SonarQube |

---

## Hexagonal Architecture

The project follows the **Ports & Adapters (Hexagonal) pattern** — the domain and application layers are completely isolated from framework and infrastructure concerns.

### Layer Overview

```
com.devcool/
├── domain/              # Pure business logic — zero Spring/JPA/AWS imports
│   ├── auth/            # JWT tokens, refresh tokens
│   ├── user/            # User, Role, UserStatus
│   ├── channel/         # Channel types (Forum, Lounge, PrivateChat)
│   ├── chat/            # Messages
│   ├── member/          # Channel membership
│   ├── media/           # Media upload (S3)
│   └── common/          # Shared domain primitives
│
├── application/
│   └── service/         # Use case implementations — wires domain ports together
│
└── adapters/
    ├── in/
    │   ├── web/          # REST controllers + request/response DTOs (MapStruct mappers)
    │   └── websocket/    # WebSocket handlers + auth handshake interceptor
    └── out/
        ├── persistence/  # JPA entities + Spring Data repositories
        ├── storage/      # S3StorageAdapter (AWS SDK v2)
        ├── jwt/          # JWT issuance and validation
        └── crypto/       # BCrypt password hashing
```

### Port Pattern

Each domain module defines:

- **Inbound ports** (`UseCase` / `Query` interfaces) — called by `adapters/in`
- **Outbound ports** (`Port` interfaces) — implemented by `adapters/out`

Application services implement inbound ports and depend **only** on outbound port interfaces — never directly on JPA repositories or the AWS SDK.

```
HTTP Request
    │
    ▼  adapters/in/web/controller        (HTTP → command object)
    │
    ▼  domain/.../port/in/UseCase        (inbound port interface)
    │
    ▼  application/service/...Service    (implements UseCase, calls Port)
    │
    ▼  domain/.../port/out/...Port       (outbound port interface)
    │
    ▼  adapters/out/...Adapter           (JPA / S3 / JWT implementation)
```

### Example: Media Upload Request Flow

```
POST /api/v1/medias/upload (MultipartFile + channelId)
  │
  ▼ MediaController
    - Extracts userId from Spring Security principal
    - Calls MediaDtoMapper.toUploadMediaCommand(file, userId, channelId)
  │
  ▼ UploadMediaUseCase  [inbound port]
  │
  ▼ MediaService
    - Validates file not empty → InvalidMediaContentException
    - Validates contentType in allowlist → UnsupportedMediaTypeException
    - Builds S3 key: "channel/{channelId}/{yyyy/MM/dd}/{uuid}.{ext}"
    - Calls MediaStoragePort.upload(UploadRequest)
  │
  ▼ MediaStoragePort  [outbound port]
  │
  ▼ S3StorageAdapter
    - Builds AWS PutObjectRequest
    - Calls s3Client.putObject(...)
    - Returns UploadResult(bucket, objectKey)
  │
  ▼ HTTP 200 { "data": { "key": "channel/123/2026/03/08/uuid.jpg" } }
```

### Boundaries enforced

| Boundary | Rule |
|---|---|
| Domain | No Spring, JPA, or AWS imports |
| Application service | Depends only on port interfaces — never on `@Repository` beans or SDKs |
| Controllers | Only map HTTP types → domain commands; no business logic |
| Domain exceptions | Thrown from the service layer; caught by a global `@ControllerAdvice` |

---

## AWS Architecture

`dev` runs in **us-east-1**, built by Terraform (`infra/`, ADR-0003) and deployed by GitHub Actions through OIDC: no AWS keys are stored in GitHub. Design: [phase 2](docs/plans/phases/phase-2-infra-cicd-walking-skeleton.md), ADR-0002, ADR-0013.

```
            https://<domain>  (Route 53 + ACM)
                     │
              ┌──────┴──────┐
              │ CloudFront  │── default ──▶ S3 (SPA bucket)
              └──────┬──────┘
     /api/*, /ws, /actuator/health  (+ X-Origin-Verify header)
                     │
┌─ VPC, 2 AZs ───────┼──────────────────────────────────────┐
│  public subnets    ▼                                      │
│             ALB  origin.<domain>       NAT gateway        │
│                    │                        ▲             │
│  private subnets   ▼                        │ egress      │
│        ECS Fargate: api (1–2 tasks) ────────┘             │
│                    │      └──▶ VPC endpoints: ECR, Logs,  │
│                    ▼           Secrets Manager, S3        │
│        Aurora PostgreSQL Serverless v2 (0–4 ACU)          │
└───────────────────────────────────────────────────────────┘
```

The diagram shows the awake state. `sleep` scales `api` to 0 tasks. `hibernate` destroys the ALB and ECS and turns off the NAT and the interface endpoints, leaving CloudFront, S3 and a paused Aurora.

| Component | Details |
|---|---|
| **CloudFront** (`edge`) | One distribution: the SPA from S3, the API paths to the ALB. Survives hibernate, so the SPA still loads while the API returns 502 |
| **ALB** (`app`) | HTTPS only; rejects requests without CloudFront's `X-Origin-Verify` header (403). Health check: `/actuator/health/readiness` |
| **ECS Fargate** (`app`) | `api` service, 0.5 vCPU / 1 GB, 1–2 tasks on CPU target tracking, private subnets, read-only root filesystem |
| **Aurora Serverless v2** (`data`) | PostgreSQL, 0–4 ACU: pauses when idle; a resume after a long pause can take ≥ 30 s, so the client timeouts allow 60 s |
| **S3** (`data`, `edge`) | Private media bucket (presigned URLs) and the SPA bucket |
| **ECR** (`bootstrap`) | `devcool` repository, immutable `:<git sha>` tags |
| **Secrets Manager** (`data`) | The RDS-managed DB secret and the JWT secrets, injected as task `secrets` |

Security groups chain alb → api → aurora; tasks reach AWS APIs through the endpoints or the NAT.

### Deployment flow

| Change | Workflow | What happens |
|---|---|---|
| Backend merge to `master` | `deploy.yml` | build and push `:<sha>` → run the `ecs,migrate` task → roll `api` (circuit breaker rolls back) → smoke `/actuator/health`. Asleep: no smoke. Hibernated: only registers the revision; `resume` migrates and rolls it out |
| PR touching `infra/` | `infra-plan.yml` | `terraform plan` per changed stack, posted as a PR comment |
| Infra merge to `master` | `infra-apply.yml` | apply every stack, then roll `api` onto the new task definition (`app` skipped while hibernated) |
| Manual, nightly | `env-power.yml` | `sleep` / `wake` / `hibernate` / `resume`; hibernated is the default state |

Migrations run as their own one-off task before the rollout, so the app never runs Flyway on start (`ecs` profile). Terraform doesn't own the image tag: `deploy.yml` registers each new revision.

Commands, first-time setup, costs and gotchas: [`infra/README.md`](infra/README.md). To run the production image locally with the `ecs` profile: [`docker/ecs/README.md`](docker/ecs/README.md).

### Environment variables (ECS task definition)

Set in `infra/stacks/app/service.tf`. Secret values are Secrets Manager ARNs that ECS resolves at task start.

| Variable | Source | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | env | `ecs` (`ecs,migrate` for the migrate task) |
| `SPRING_DATASOURCE_URL` | env | Aurora writer endpoint, `sslmode=require`, raised connect timeouts for resume |
| `AWS_S3_BUCKET` | env | Media bucket name |
| `WS_ALLOWED_ORIGINS` | env | `https://<domain>`: the app sees `Host: origin.<domain>` behind CloudFront (allowlist: P4-T06) |
| `SPRING_DATASOURCE_USERNAME` | secret | RDS-managed secret, `username` key |
| `SPRING_DATASOURCE_PASSWORD` | secret | RDS-managed secret, `password` key |
| `JWT_ACCESS_SECRET` | secret | `devcool/<env>/jwt-access`: standard Base64 of ≥ 32 bytes (`openssl rand -base64 48`) |
| `JWT_REFRESH_SECRET` | secret | `devcool/<env>/jwt-refresh`: same format |

---

## Local Development

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for PostgreSQL via Docker Compose)
- AWS credentials configured (for S3 — can use a local bucket or LocalStack)

### Setup

1. Clone the repository.
2. Copy `local.env` and fill in your JWT secrets and AWS credentials.
3. Start the application — Spring Boot will automatically start the PostgreSQL container:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

Docker Compose spins up:
- **PostgreSQL 15** on `localhost:5432`
- **pgAdmin 4** on `http://localhost:5050`

### Useful commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Format code
./mvnw spotless:apply

# Check formatting
./mvnw spotless:check

# Static analysis (SpotBugs)
./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify
```

---

## Running Tests

```bash
# Unit tests only
./mvnw test

# Unit + integration tests (*IT classes; needs Docker) + coverage floor
./mvnw -Dit verify

# Unit tests + coverage report (integration tests don't run without -Dit)
./mvnw verify
```

Coverage report is generated at `target/site/jacoco/index.html`.

### Integration tests

`*IT` classes need Docker running (Testcontainers starts `pgvector/pgvector:pg16`).

**Docker Desktop on Linux:** if Ryuk fails to start, run this, then restart the machine:

```bash
echo 'TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock' >> ~/.config/environment.d/docker.conf
```

### CI Pipeline

Every pull request and every push to `master` runs `.github/workflows/ci.yml`. Any failing step fails the run:

1. Spotless format check
2. Unit tests + integration tests + JaCoCo line-coverage floor + SpotBugs, in one `./mvnw -Dit -Pstatic-analysis verify`
3. SonarCloud analysis (always; needs the `SONAR_TOKEN` repo secret)
4. Test and JaCoCo reports uploaded as artifacts

---

## API Docs

Swagger UI is available at `/docs` once the application is running.

> Full API reference will be added here.
