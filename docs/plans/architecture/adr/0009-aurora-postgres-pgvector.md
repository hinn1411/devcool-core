# 0009 — Aurora Serverless v2 PostgreSQL with pgvector

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P1 (local pgvector image), P2 (Aurora), P8 (vectors)

## Context
Postgres 15 is the database today (local Docker; the README mentions RDS `db.t3.micro`). Phase 8 needs vector search with a permission filter joined to relational data. "Scale to zero where possible."

## Options considered

### Relational database
| | **Aurora Serverless v2 PG 16** | RDS PostgreSQL (provisioned) | DynamoDB for messages |
|---|---|---|---|
| Pros | Min 0 ACU with auto-pause (dev) or 0.5 ACU (prod). Storage autoscaling. Fast failover with a reader. pgvector supported | Cheapest at steady load. Simple | Unlimited scale; the textbook chat store at huge scale |
| Cons | Resume after pause takes seconds. ACU-hour price is higher at constant load | Always billed. Manual storage sizing | Loses transactions across seq + message + outbox. Access patterns must be designed up front. Kills the relational model the code uses |

### Vector store
| | **pgvector (same DB)** | OpenSearch Serverless | Pinecone / Qdrant Cloud |
|---|---|---|---|
| Pros | Permission filter is SQL. Chunks and messages in one transaction/backup. Nothing new to run | Hybrid BM25 + kNN built in. Scales far | Purpose-built, fast, managed |
| Cons | Filtered HNSW needs iterative scans (pgvector ≥ 0.8). Ceiling ~10⁷ vectors on one instance | High minimum OCU cost, never zero. Another store to sync | A second store to keep consistent with permissions; data leaves AWS |

## Decision
**Aurora Serverless v2 (PostgreSQL 16) with the `vector` extension**:
- dev: min 0 ACU (auto-pause after 10 min), max 4.
- prod: min 0.5, max 8, one reader in a second AZ.

Credentials are an RDS-managed secret in Secrets Manager. Locally and in Testcontainers: `pgvector/pgvector:pg16`.

## Consequences
- Upgrading local Postgres 15 → 16 is part of P1 (fresh local volume; no production data exists yet).
- `CREATE EXTENSION vector` goes in a Flyway migration. Aurora allows it for the master user.
- **Verify at P8 start:** the pgvector version bundled with the chosen Aurora PG minor version (`SELECT extversion FROM pg_extension WHERE extname='vector'`). The design assumes ≥ 0.8 for `hnsw.iterative_scan`.
- Connection pooling: Hikari max pool × tasks must stay under Aurora's `max_connections` for the ACU floor. Consider RDS Proxy if tasks scale wide.

## Revisit when
- Vector count > ~10M, or p99 vector query latency > 200 ms after tuning → dedicated vector store.
- Message table > ~500M rows → partitioning (`channel_id` hash), then a wide-column store.
