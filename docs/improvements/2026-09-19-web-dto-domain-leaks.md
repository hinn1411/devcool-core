# Improvements — Domain Types in Web Response DTOs

**Date:** 2026-09-19
**Scope:** `adapters/in/web/dto/response/` (pre-existing, not introduced by `feat/list-messages-in-a-chat-room`)

---

## Medium

### 1. `GetProfileResponse` exposes domain enums `Role` and `UserStatus`
**Files:**
- `adapters/in/web/dto/response/GetProfileResponse.java`
- `adapters/in/web/dto/mapper/AuthDtoMapper.java` (`toProfileResponse`)

`GetProfileResponse` declares `private Role role` and `private UserStatus status`, both from `com.devcool.domain.user.model.enums`, and references them in `@Schema(implementation = ...)`. Renaming or adding a constant in the domain enum silently changes the public API contract and the OpenAPI docs.

**Fix:** follow the same pattern as `ChannelListItemResponse` / `MessageItemResponse`:
- Change the fields to `String role` and `String status`.
- In `AuthDtoMapper.toProfileResponse`, map with `user.getRole().name()` and `user.getStatus().name()` (null-safe if either can be null).
- Replace `implementation = Role.class` / `UserStatus.class` in `@Schema` with `allowableValues = {...}` if the docs should still list the values.
- Remove the domain imports.

JSON output stays the same (`"USER"`, `"ACTIVE"`), since Jackson already serializes enums by name.

**Check afterwards:** `grep -rn "import com.devcool.domain" src/main/java/com/devcool/adapters/in/web/dto/response/` returns nothing.

---

## Found while checking (related)

### 2. `GetProfileResponse.id` is never set
`AuthDtoMapper.toProfileResponse` does not call `.id(...)`, so `id` is always null and dropped by `@JsonInclude(NON_NULL)` — the profile response has no user id. Add `.id(String.valueOf(user.getId()))`, or change the field type to `Integer` to match other responses.

### 3. `CreateChannelResponse` reuses the schema name `GetProfileResponse`
`adapters/in/web/dto/response/CreateChannelResponse.java` is annotated `@Schema(name = "GetProfileResponse", description = "Get profile result payload")` — a copy-paste leftover. Two classes with the same schema name collide in the generated OpenAPI spec, so one overwrites the other in Swagger UI. Rename to `@Schema(name = "CreateChannelResponse", description = "Create channel result payload")`.

### 4. Wildcard Lombok imports
`GetProfileResponse` and `CreateChannelResponse` use `import lombok.*;`. Replace with explicit imports to match the rest of the codebase and avoid Checkstyle warnings.
