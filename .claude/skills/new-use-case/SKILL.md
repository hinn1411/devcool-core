---
name: new-use-case
description: Scaffold a new hexagonal use case in the DevCool backend (inbound port, command, service, outbound port, adapter, controller/DTO/MapStruct mapper, Mockito test) following the existing media-upload pattern. Use when adding a new backend capability or endpoint.
argument-hint: <domain-area> <UseCaseName>, e.g. chat EditMessage
arguments: [area, name]
---

# New use case: $name in domain `$area`

Follow the reference implementation exactly (package layout, naming, record style, error handling). Read these first:
- `src/main/java/com/devcool/domain/media/port/in/UploadMediaUseCase.java` and `domain/media/port/in/command/`
- `src/main/java/com/devcool/application/service/MediaService.java`
- `src/main/java/com/devcool/domain/media/port/out/MediaStoragePort.java`
- `src/main/java/com/devcool/adapters/out/storage/S3StorageAdapter.java`
- `src/main/java/com/devcool/adapters/in/web/controller/MediaController.java`
- `src/main/java/com/devcool/adapters/in/web/dto/mapper/MediaDtoMapper.java`
- `src/test/java/com/devcool/application/service/MediaServiceTest.java`
- `docs/architecture-request-flow.md` (the full request flow)

Existing domain areas: !`ls src/main/java/com/devcool/domain`

## Files to create (skip any that already exist; extend instead)

In the paths below, `<Name>` = `$name` and `<area>` = `$area`.

| # | File | Content |
|---|---|---|
| 1 | `domain/<area>/port/in/<Name>UseCase.java` | Interface, one method. Command in → domain result out. No Spring types |
| 2 | `domain/<area>/port/in/command/<Name>Command.java` | `record` with validated fields (compact constructor checks invariants) |
| 3 | `domain/<area>/port/out/<Dep>Port.java` | Only if a new external dependency is needed. Nested request/result records |
| 4 | `domain/<area>/exception/*.java` | Domain exceptions extending `DomainException`, with a new `ErrorCode` where needed |
| 5 | `application/service/.../<Name>Service.java` (or a method on an existing service in that area) | `@Service`, implements the use case, `@Transactional` if it writes. Order: load → exists? → authorized? → act |
| 6 | `adapters/out/...` | Implementation of any new outbound port |
| 7 | `adapters/in/web/dto/{request,response}/*.java` | HTTP DTOs with Bean Validation |
| 8 | `adapters/in/web/dto/mapper/<Area>DtoMapper.java` | MapStruct methods DTO ↔ command/result |
| 9 | `adapters/in/web/controller/<Area>Controller.java` | Endpoint: caller id from `Authentication`, map → use case → `ApiResponseFactory.success(...)` |
| 10 | `adapters/in/web/util/HttpErrorMapper.java` | HTTP status for each new `ErrorCode` |
| 11 | `src/test/.../<Name>ServiceTest.java` | `@ExtendWith(MockitoExtension.class)`: happy path, not found, forbidden, validation |
| 12 | `src/test/.../<Area>DtoMapperTest.java` | Mapping cases for the new methods |

## Checks before finishing
- `domain/**` has no Spring/JPA/AWS imports.
- The service injects ports only (no repositories).
- The controller returns DTOs, not domain objects.
- Every `ErrorCode` is mapped.
- Run `./mvnw spotless:apply` and `./mvnw test`.
