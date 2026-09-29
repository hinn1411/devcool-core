# 0004 — Monorepo: backend at root, `frontend/`, `infra/`

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P0

## Context
The repo is a single Maven project at the root. A React SPA and Terraform are being added.

## Options considered

### A — Monorepo, backend stays at the root
- Pros: one PR can change API + UI + infra atomically. One CI with path filters. Claude Code sees both sides of every contract (OpenAPI → TS client). No backend move, so history, CI and docs stay valid.
- Cons: CI must use path filters to stay fast. The root mixes Maven files with other folders.

### B — Monorepo, move backend into `backend/`
- Pros: symmetrical layout.
- Cons: touches every path in CI, Dockerfile, docs and IDE configs for no functional gain now.

### C — Separate repos
- Pros: independent release cadence, clean ownership.
- Cons: cross-repo changes and version coordination. Claude sees one side at a time.

## Decision
**A.** Layout:

```
/                 Spring Boot backend (pom.xml, src/)
/frontend         React + Vite SPA            (own CLAUDE.md)
/infra            Terraform                   (own CLAUDE.md)
/docker           local compose files
/docs             learning, improvements, plans
/.claude          Claude Code config shared by all
```

## Consequences
- CI uses `dorny/paths-filter` (or `on.paths`) so a frontend change doesn't run Maven.
- Per-directory `CLAUDE.md` in `frontend/` and `infra/` loads only when Claude works there.
- Commit subjects keep the existing conventional style; the scope names the area when it helps (`feat(frontend): …`).

## Revisit when
- The backend moves to a multi-module build: that's the natural time for option B.
