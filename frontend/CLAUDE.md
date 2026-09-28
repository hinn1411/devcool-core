# frontend/ — React SPA

The app is scaffolded in P5-T01 (`docs/plans/phases/phase-5-frontend.md`). Until then this directory only holds this file.

## Stack
React 19 + Vite + TypeScript (strict) · TanStack Query · Zustand · React Router · Tailwind + shadcn/ui · react-virtuoso · openapi-typescript/openapi-fetch · Vitest + React Testing Library + MSW · Playwright.

## Commands (run from `frontend/`)
```bash
npm ci
npm run dev          # Vite dev server; proxies /api and /ws to http://localhost:8080
npm run lint
npm run typecheck
npm run test -- --run
npm run build
npm run gen:api      # regenerate src/api/schema.d.ts from the running backend's /v3/api-docs
```

## Conventions
- **Server data** lives in TanStack Query; **client/UI state** (connection, presence, typing) lives in Zustand. Don't copy server data into Zustand.
- The API types come from `npm run gen:api`. Never hand-write types for backend DTOs.
- **Auth:** the access token is kept in memory only (never localStorage). The refresh token is an HttpOnly cookie sent automatically to `/api/v1/auth/*`. Refresh is single-flight.
- **WebSocket:** all socket code lives in `src/ws/` and follows protocol v2 (`.claude/rules/websocket-protocol.md`, the `ws-protocol` skill). Components never touch `WebSocket` directly.
- WS events are merged into the Query cache deduped by `messageId` and ordered by `seq`. A seq gap triggers `RESUME`.
- Messages render markdown through `react-markdown` + `rehype-sanitize`. Never use `dangerouslySetInnerHTML`.
- Components go in `src/features/<area>/`; shared primitives in `src/components/ui/`.
- Tests sit next to their code (`*.test.ts(x)`). Mock REST with MSW and WS with the fake socket in `src/ws/testing/`.
