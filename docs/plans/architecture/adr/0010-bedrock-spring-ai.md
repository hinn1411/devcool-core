# 0010 — Amazon Bedrock through Spring AI, behind ports

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P8

## Context
Phase 8 needs text generation (streaming), embeddings, and structured output. The app runs on ECS in `ap-southeast-1`. The user is studying for an AWS GenAI certification.

## Options considered

### Provider
| | **Amazon Bedrock** | Anthropic API direct | OpenAI |
|---|---|---|---|
| Pros | IAM task-role auth (no API keys). VPC interface endpoint. Claude + Titan/Cohere embeddings in one place. Guardrails available. AWS cert alignment | Newest Claude models and features first. Simple SDK | Broad model range |
| Cons | Per-region model availability; may need cross-region inference profiles. Quotas need requesting | API key in Secrets Manager. Separate embedding provider | Same as direct, plus a different vendor for embeddings/chat |

### Java framework
| | **Spring AI** | LangChain4j | Raw AWS SDK (`BedrockRuntimeAsyncClient`) |
|---|---|---|---|
| Pros | Spring Boot autoconfig. `ChatClient` fluent API. `Flux` streaming. Observations → Micrometer token metrics. Ollama and Bedrock clients with one abstraction | Rich RAG toolkit; framework-agnostic | No abstraction to learn; full control |
| Cons | API still evolving; must pick the line compatible with Boot 3.5 | Less Spring-native | You write streaming, retries and metrics yourself |

## Decision
**Bedrock via Spring AI's Bedrock Converse chat client and Bedrock Titan embedding client**, wrapped in our own ports (`LlmStreamingPort`, `EmbeddingPort`). Spring AI types never leave `adapters/out/ai`.

- Chat: Claude Sonnet (RAG answers), Claude Haiku (summaries, tags, eval judge). Model ids come from config.
- Embeddings: Titan Text Embeddings v2, 1024 dimensions, normalized.
- Local: Spring AI Ollama clients under profile `local` (see [04 §9](../04-genai-rag-design.md#9-local-development)).
- IAM: `bedrock:InvokeModel` and `bedrock:InvokeModelWithResponseStream` on the specific model/inference-profile ARNs only.

## Consequences
- **Before starting P8:** confirm model access is enabled in the Bedrock console for the region, and check which Spring AI version supports Boot 3.5 (context7).
- The ports mean unit tests never touch a model, and swapping to the Anthropic API later is an adapter change.
- Token usage and latency metrics come from Spring AI observations plus our own `AiUsagePort`.

## Revisit when
- A needed model or feature is unavailable on Bedrock in the region → add an Anthropic API adapter behind the same port.
