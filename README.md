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
| Database | PostgreSQL 15 |
| ORM / Migrations | Spring Data JPA + Flyway |
| Auth | JWT (nimbus-jose-jwt) |
| Storage | AWS S3 (SDK v2, region: `ap-southeast-1`) |
| WebSocket | Spring WebSocket (raw, not STOMP) |
| Code generation | Lombok + MapStruct 1.5.5 |
| API Docs | SpringDoc OpenAPI (Swagger UI at `/docs`) |
| Build | Maven 3.9+ |
| Code quality | Spotless (Google Java Format), Checkstyle, PMD, SpotBugs |
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

The production deployment runs on **AWS ap-southeast-1 (Singapore)** using ECS Fargate, RDS PostgreSQL, and S3.

```
                  Internet
                     │
                     ▼
              ┌─────────────┐
              │   Internet   │
              │   Gateway    │
              └──────┬───────┘
                     │
         ┌───────────┴───────────┐
         │    VPC 10.0.0.0/16    │
         │                       │
         │  ┌─────────────────┐  │
         │  │  Public Subnet  │  │
         │  │  10.0.1.0/24    │  │
         │  │  (AZ-a)         │  │
         │  │                 │  │
         │  │  ┌───────────┐  │  │
         │  │  │ ECS Task  │  │  │
         │  │  │ (Fargate) │──┼──┼──→ S3 (via Internet)
         │  │  │ Public IP │  │  │
         │  │  └─────┬─────┘  │  │
         │  └────────┼────────┘  │
         │           │           │
         │  ┌────────┼────────┐  │
         │  │  Private Subnet │  │
         │  │  10.0.10.0/24   │  │
         │  │  (AZ-a)         │  │
         │  │  ┌───────────┐  │  │
         │  │  │    RDS    │  │  │
         │  │  │ PostgreSQL│  │  │
         │  │  └───────────┘  │  │
         │  └─────────────────┘  │
         │                       │
         │  ┌─────────────────┐  │
         │  │  Private Subnet │  │
         │  │  10.0.11.0/24   │  │
         │  │  (AZ-b, RDS     │  │
         │  │   subnet group) │  │
         │  └─────────────────┘  │
         └───────────────────────┘
```

### Components

| Component | Details |
|---|---|
| **ECS Fargate** | Spring Boot container in a public subnet; 0.5 vCPU / 1 GB RAM; exposes port 8080 |
| **RDS PostgreSQL 15** | `db.t3.micro` in a private subnet; only reachable from the ECS security group |
| **S3** | Private bucket `devcool-media`; application uses presigned URLs for client access |
| **ECR** | Docker image registry for the Spring Boot container |
| **IAM Task Role** | Grants the ECS task `s3:PutObject` and `s3:GetObject` on the media bucket |

### Networking

- 1 public subnet (ECS tasks — needs internet access for S3 and inbound traffic)
- 2 private subnets in different AZs (required by RDS DB subnet group)
- ECS security group: inbound TCP 8080 from `0.0.0.0/0`
- RDS security group: inbound TCP 5432 from the ECS security group only

### Deployment flow

```bash
# Build and push image to ECR
docker build -t devcool .
docker tag devcool:latest <ACCOUNT_ID>.dkr.ecr.ap-southeast-1.amazonaws.com/devcool:latest
docker push <ACCOUNT_ID>.dkr.ecr.ap-southeast-1.amazonaws.com/devcool:latest

# Force ECS to deploy the new image
aws ecs update-service \
  --cluster devcool-cluster \
  --service devcool-service \
  --force-new-deployment \
  --region ap-southeast-1
```

### Environment variables (ECS task definition)

| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `ecs` |
| `SPRING_DATASOURCE_URL` | RDS JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | RDS username |
| `SPRING_DATASOURCE_PASSWORD` | RDS password |
| `JWT_ACCESS_SECRET` | Min 32-char secret for access tokens |
| `JWT_REFRESH_SECRET` | Min 32-char secret for refresh tokens |
| `AWS_S3_BUCKET` | S3 bucket name |

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

# Static analysis (Checkstyle, PMD, SpotBugs)
./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify
```

---

## Running Tests

```bash
# Unit tests only
./mvnw test

# Integration tests only
./mvnw -Dit verify

# All tests + coverage report
./mvnw verify
```

Coverage report is generated at `target/site/jacoco/index.html`.

### CI Pipeline

Every push triggers the GitHub Actions workflow (`.github/workflows/ci.yml`):

1. Spotless format check
2. Static analysis (Checkstyle / PMD / SpotBugs)
3. Unit tests + integration tests
4. JaCoCo coverage report (uploaded as artifact)
5. SonarQube analysis (when `SONAR_TOKEN` is set)

---

## API Docs

Swagger UI is available at `/docs` once the application is running.

> Full API reference will be added here.
