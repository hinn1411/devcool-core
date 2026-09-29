# Phase 8 — GenAI: "Ask DevCool"

**Weeks:** 8–11 · **Depends on:** P6 (events), P4 (WS protocol), P3 (seq, versions, read state) · **ADRs:** 0009, 0010 · **Design:** [04-genai-rag-design.md](../architecture/04-genai-rag-design.md)

## Goal
Question answering over the chat history a user is allowed to see, with citations, streamed over the WebSocket. It is built in five sub-phases; each one is demoable on its own.

## Why it matters (interview angle)
The GenAI part isn't "call an LLM". It's the backend around it:
- Event-driven indexing.
- Consistency under edit/delete.
- Idempotency.
- Authorization at retrieval.
- Streaming with cancellation.
- Resilience.
- Cost limits.
- An evaluation harness.

**Steps 8a–8b alone are a strong CV bullet.**

## Prerequisites
- Bedrock model access granted in the region (requested in P2).
- The pgvector version on Aurora checked (ADR-0009).
- The Spring AI version compatible with Boot 3.5 confirmed via context7.

---

## 8a — Indexing pipeline (week 8)
**Goal:** every message change is reflected in `message_chunk` embeddings within seconds, exactly once in effect.

- [ ] **P8-T01** Migration: `CREATE EXTENSION vector`; the `message_chunk` table with HNSW + GIN indexes (see design §2)
- [ ] **P8-T02** `domain/ai` ports and models. `ChunkBuilder`, a pure function with exhaustive unit tests: time gaps, token cap, overlap, replies, deleted messages, media captions
- [ ] **P8-T03** Spring AI dependency + `BedrockEmbeddingAdapter` (profile `ecs`) and an Ollama embedding adapter (profile `local`) behind `EmbeddingPort`. Ollama in compose (`--profile ai`) with `mxbai-embed-large`
- [ ] **P8-T04** `PgVectorChunkStoreAdapter` (JdbcTemplate): upsert with the version guard, find by message id, delete empty chunks
- [ ] **P8-T05** `ChunkIndexingService` + `indexer` SQS consumer (on the P6 base): dedupe → rebuild from the DB → hash compare → embed → upsert
- [ ] **P8-T06** Backfill: `ChannelReindexRequested` event + an admin endpoint; re-index where `embedding_model != current`
- [ ] **P8-T07** Terraform: the `indexer` queue + DLQ + filter policy; IAM `bedrock:InvokeModel` for the embedding model ARN; Bedrock VPC interface endpoint

**DoD 8a:** create/edit/delete in the UI → the chunk rows change accordingly (verified in an IT with a fake `EmbeddingPort`). Duplicate and out-of-order events are handled (tests).

## 8b — Semantic search, no LLM (week 8–9)
**Goal:** a demoable "search by meaning" with permission filtering.

- [ ] **P8-T08** `ChannelAccessPort` (member channel ids, cached per request) + `SemanticSearchService`
- [ ] **P8-T09** Vector query with `channel_id = ANY(:allowed)`, `hnsw.iterative_scan`, and a similarity threshold. `GET /api/v1/search/semantic?q=&limit=` returns the chunks with their message ids
- [ ] **P8-T10** **Leak test (must exist):** a non-member never receives chunks from a private channel, even with an exact-match query. Also test that a removed member loses access immediately
- [ ] **P8-T11** Frontend: a semantic toggle in the search box; results jump to the messages

**DoD 8b:** a search demo in the deployed app, and the leak test in CI.

## 8c — RAG answers with citations, streamed (week 9–10)
- [ ] **P8-T12** `LlmStreamingPort` + `BedrockChatAdapter` (Converse streaming, Claude Sonnet) and an Ollama chat adapter for local
- [ ] **P8-T13** `AskService`: access → embed → retrieve → build the prompt (delimited untrusted sources, citation rules) → stream. Validate citations against the retrieved ids
- [ ] **P8-T14** WS frames `ASK`, `ASK_CANCEL` → `AI_CHUNK`, `AI_DONE{citations, usage}`, `AI_ERROR`. Cancel on socket close
- [ ] **P8-T15** Frontend Ask panel (P5-T12): streaming text, citation chips, cancel

**DoD 8c:** a streamed answer with working citation links in the deployed app. Cancelling stops the Bedrock stream (visible in metrics).

## 8d — Hardening (week 10–11)
- [ ] **P8-T16** Resilience4j around Bedrock: time limiter (5 s to first token, 30 s total), retry once on throttling before the first token, circuit breaker. Fallback = search results with `fallback: true`
- [ ] **P8-T17** Bucket4j limits (20 asks/hour/user) + a daily output-token budget in Valkey; `NACK{RATE_LIMITED}`/`AI_ERROR{BUDGET_EXCEEDED}`
- [ ] **P8-T18** Metrics: `gen_ai.client.token.usage`, TTFT, fallback count, retrieval hits; the GenAI dashboard panel (P7)
- [ ] **P8-T19** Evaluation harness: seed conversations + a golden set (~30 questions), a `@Tag("eval")` suite, and an LLM-as-judge (Haiku) for groundedness. The `eval.yml` workflow runs on dispatch. Record the results in `docs/plans/eval-results.md`
- [ ] **P8-T20** Prompt-injection tests: a source message containing "ignore previous instructions…" doesn't change the behaviour; the answer still cites only real ids

**DoD 8d:** eval targets met (design §7). Zero leakage. The rate limit and fallback are demonstrated.

## 8e — Secondary features (week 11, as time allows)
- [ ] **P8-T21** "Catch me up": summarize unread messages (from `last_read_seq`) with Haiku; cached per `(channelId, fromSeq, toSeq)`
- [ ] **P8-T22** Duplicate question hint: debounced similarity search while composing a question; no LLM
- [ ] **P8-T23** Auto-tagging: Haiku with structured output (JSON schema, enum of tags); validated and retried once; tags shown on channels/threads

---

## Files touched
- `domain/ai/**`
- `application/service/ai/**`
- `adapters/out/ai/**`, `adapters/out/persistence/ai/**`
- `adapters/in/messaging/IndexerSqsListener.java`
- `adapters/in/websocket/**` (ASK frames)
- `adapters/in/web/controller/SearchController.java`
- `db/migration/V*`
- `infra/stacks/{network,data,app}`
- `frontend/src/features/ask/**`
- `src/test/resources/eval/**`

## Test plan
- **Unit:** ChunkBuilder, prompt builder, citation validator, AskService with mocked ports (success, empty retrieval, LLM failure → fallback, cancel).
- **IT:** indexing via LocalStack SQS + pgvector with a deterministic fake embedder; the leak tests; the WS ASK flow with a fake `LlmStreamingPort` emitting tokens.
- **Eval:** golden set against real Bedrock (manual/nightly).

## Definition of Done
Milestone **M4**: Ask DevCool is live in dev AWS with citations, streaming and cancel. The leak and injection tests are in CI. Eval numbers are recorded.

## Interview talking points
- Permission filtering inside retrieval; filtered-HNSW under-return and iterative scans.
- Chunking for chat; why not embed single messages.
- Keeping vectors consistent with edits/deletes; idempotent consumers; re-index on model change.
- Streaming over WS with backpressure and cancellation; TTFT as a metric.
- Evaluating RAG: recall@k, citation precision, groundedness, refusal accuracy.
- Cost control: rate limits, budgets, model tiering (Sonnet vs Haiku).
- Prompt injection from user-generated content.

## Risks
- Bedrock quotas/region availability: request early; use a cross-region inference profile.
- Spring AI API churn: keep it confined to adapters.
- Eval set too small or too easy: include unanswerable and adversarial questions.
