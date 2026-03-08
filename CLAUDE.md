# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

This is a **Spring Boot 3.5.6 / Java 21** REST + WebSocket API using **Hexagonal Architecture (Ports & Adapters)**.

### Layer Structure

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

### Domain Port Pattern

Each domain defines:
- **Inbound ports** (`UseCase` / `Query` interfaces) — called by adapters/in
- **Outbound ports** (`Port` interfaces) — implemented by adapters/out

Application services implement inbound ports and depend only on outbound port interfaces.

### Request Flow Through All Layers

Concrete example: `POST /api/v1/medias/upload` (media upload)

```
HTTP Request (MultipartFile + channelId)
  │
  ▼ adapters/in/web/controller/MediaController.java
    - Extracts userId from Spring Security Authentication principal
    - Calls MediaDtoMapper.toUploadMediaCommand(file, userId, channelId)
  │
  ▼ adapters/in/web/dto/mapper/MediaDtoMapper.java
    - Maps MultipartFile metadata → UploadMediaCommand record
      (file, size, contentType, userId, channelId)
  │
  ▼ domain/media/port/in/UploadMediaUseCase  [INBOUND PORT interface]
  │
  ▼ application/service/MediaService.java    [implements UploadMediaUseCase]
    - Validates file not empty → throws InvalidMediaContentException
    - Validates contentType in allowlist (jpeg/png/webp/mp4) → throws UnsupportedMediaTypeException
    - Builds S3 key: "channel/{channelId}/{yyyy/MM/dd}/{uuid}{ext}"
    - Opens file InputStream
    - Constructs MediaStoragePort.UploadRequest(key, stream, size, contentType)
    - Calls storagePort.upload(request)
  │
  ▼ domain/media/port/out/MediaStoragePort  [OUTBOUND PORT interface]
  │
  ▼ adapters/out/storage/S3StorageAdapter.java  [implements MediaStoragePort]
    - Reads bucket name from AwsProps config
    - Builds AWS SDK PutObjectRequest (bucket, key, contentType)
    - Calls s3Client.putObject(request, RequestBody.fromInputStream(...))
    - Returns UploadResult(bucket, objectKey)
  │
  ▼ Back in MediaService → returns S3 object key string
  │
  ▼ Back in MediaController
    - Wraps result with ApiResponseFactory.success(...)
  │
  ▼ HTTP 200 { "data": { "key": "channel/123/2026/03/08/uuid.jpg" } }
```

**Key contracts at each boundary:**
- Controller → Service: `UploadMediaCommand` record (domain object, no HTTP types)
- Service → Storage adapter: `MediaStoragePort.UploadRequest` record (defined inside the port interface)
- Domain exceptions (`InvalidMediaContentException`, `UnsupportedMediaTypeException`) are thrown from the service layer and handled by a global `@ControllerAdvice`

### Key Technologies

| Concern | Technology |
|---|---|
| DB | PostgreSQL 15 (Docker Compose locally) |
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

- Raw WebSocket (not STOMP) with custom message protocol
- Auth via `WsAuthHandShakeInterceptor`
- Message types: Subscribe, Unsubscribe, SendMessage

### Profiles

| Profile | DDL | DB |
|---|---|---|
| `local` | `create-drop` | Docker Compose PostgreSQL |
| `ecs` | `validate` | RDS via env vars |

Local env vars (JWT secrets etc.) are in `local.env`.

### CI Pipeline (`.github/workflows/ci.yml`)

1. Spotless format check
2. Static analysis (Checkstyle / PMD / SpotBugs via `static-analysis` profile)
3. Build + unit tests + integration tests
4. JaCoCo coverage report + optional SonarQube
