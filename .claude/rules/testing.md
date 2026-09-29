---
paths:
  - "src/test/**/*"
---

# Test conventions (backend)

- **Unit tests** use JUnit 5 + Mockito with `@ExtendWith(MockitoExtension.class)` + AssertJ. There's no Spring context in unit tests. See `application/service/MediaServiceTest.java` and `application/service/channel/ChannelServiceTest.java` for the style.
- **Adapter tests** mock the SDK/JPA boundary (`adapters/out/storage/S3StorageAdapterTest.java`).
- **Mapper tests** follow `adapters/in/web/dto/mapper/*DtoMapperTest.java`.
- **Integration tests** end in `IT` and extend the shared Testcontainers base (added in P1-T11). Use `pgvector/pgvector:pg16`, never H2. Run them with `./mvnw -Dit verify`.
- Test names describe behaviour: `rejectsEditAfter24Hours`, `returnsSameSeqOnRetriedClientMsgId`.
- Every authorization rule gets a negative test (non-member, wrong role). Every idempotent operation gets a "called twice → one effect" test.
- Async assertions use Awaitility. Never use `Thread.sleep`.
- For concurrency tests, start the threads with a `CountDownLatch`, run ≥ 20 iterations, and assert invariants (no gaps, no duplicates), not timings.
- For GenAI code, use fake `EmbeddingPort`/`LlmStreamingPort` implementations with deterministic output. Tests never call Bedrock or Ollama unless tagged `@Tag("eval")`.
