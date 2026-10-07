---
paths:
  - "src/main/java/**/*.java"
---

# Hexagonal conventions (backend main code)

Layer rules. `src/test/java/com/devcool/architecture/HexagonalArchitectureTest.java` enforces them in `./mvnw test`; don't wait for it to catch you.

- `domain/**` imports nothing from `org.springframework`, `jakarta.persistence`, `software.amazon`, `com.fasterxml.jackson`, `application` or `adapters`. Records, plain classes, domain exceptions and port interfaces only.
- `application/service/**` depends on `domain` ports and models only. Never inject a `*Repository`, `*Entity`, an AWS client or a Spring AI type into a service, and never call an adapter's static helper. From Spring, only `stereotype` and `transaction`.
- `adapters/in/**` translates transport → command → inbound port. No business rules. No repositories.
- `adapters/out/**` implements an outbound port. No business rules; mapping and I/O only.
- `adapters/in/**` and `adapters/out/**` never import each other. Shared state between two adapters means a port is missing. A bidirectional transport (the WebSocket session map and its emitter) stays in one adapter package.
- New use case → an interface in `domain/<area>/port/in/`. New external dependency → an interface in `domain/<area>/port/out/`. The `/new-use-case` skill scaffolds both.

Existing patterns to copy (they are the reference implementation):

| Concern | Example |
|---|---|
| Inbound port + command record | `domain/media/port/in/UploadMediaUseCase.java`, `domain/media/port/in/command/` |
| Service | `application/service/MediaService.java` |
| Outbound port with nested request/result records | `domain/media/port/out/MediaStoragePort.java` |
| Adapter | `adapters/out/storage/S3StorageAdapter.java` |
| Controller + MapStruct mapper | `adapters/in/web/controller/MediaController.java`, `adapters/in/web/dto/mapper/MediaDtoMapper.java` |
| Error mapping | `domain/common/ErrorCode.java` → `adapters/in/web/util/HttpErrorMapper.java` |

Behavioural rules:
- **Authenticated is not authorized.** Every service method that reads or writes channel-scoped data checks membership (and role, for admin actions) itself. Check in this order: the resource exists → the caller is allowed → do the work.
- Domain exceptions extend `DomainException` with an `ErrorCode`. Every new `ErrorCode` needs an HTTP mapping in `HttpErrorMapper`. Never put request values such as passwords or tokens into exception details.
- `@Transactional` belongs on application service methods, not on controllers or adapters.
- Controllers return DTOs from `adapters/in/web/dto`, never domain objects (the `UserController` leak, audit item #1).
- Anything held in process memory (maps, caches, counters, `@Scheduled` jobs) must be correct when N ECS tasks run. If it isn't, it belongs in Valkey or the DB. Scheduled jobs must be profile-guarded (`worker`).
