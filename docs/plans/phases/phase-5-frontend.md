# Phase 5 — React frontend

**Weeks:** 3–8 (in parallel with P3/P4) · **Depends on:** P1 (auth fixes), P3 (APIs), P4 (protocol v2) · **ADRs:** 0004, 0005, 0011

## Goal
A simple, clean SPA that exercises every backend feature:
- Login and register.
- Channel list with unread badges.
- Message view with infinite scroll.
- Composer with optimistic send and media.
- Presence and typing.
- Reconnect/resume.
- The Ask DevCool panel.

It is deployed to S3 + CloudFront by the pipeline.

## Why it matters (interview angle)
The frontend is intentionally simple, but the **WebSocket client** isn't. A reconnecting state machine with a pending-send queue and resume is the client half of the delivery guarantee. Being able to explain both halves is rare.

## Scope
- **In:** everything listed in the goal, plus unit tests and one Playwright E2E flow.
- **Out:** mobile app, PWA/offline, i18n, SSR.

## Design notes
```
frontend/
  src/
    api/            # generated types (openapi-typescript) + openapi-fetch client + auth refresh wrapper
    ws/             # connection state machine, protocol types, pending queue, resume
    features/
      auth/  channels/  messages/  presence/  ask/
    components/ui/  # shadcn/ui
    stores/         # zustand: connection, presence, typing
    routes/
  vite.config.ts    # dev proxy: /api and /ws → http://localhost:8080 (single-origin locally too)
```

- **Auth:**
  - The access token lives in memory only.
  - On 401, call `POST /api/v1/auth/refresh_token` (the cookie is sent automatically, same origin), then retry once.
  - Refresh is single-flight (one refresh shared by concurrent 401s).
- **Server state:**
  - TanStack Query `useInfiniteQuery` for history (`beforeSeq`).
  - WS events update the cache with `queryClient.setQueryData`, deduped by `messageId` and ordered by `seq`.
- **WS client:** the state machine from [03 §10](../architecture/03-chat-system-design.md#10-reconnect-and-resume).
  - Ticket before each connect.
  - Backoff with full jitter.
  - Heartbeat every 25 s.
  - Per-channel `lastSeq` → `RESUME`.
  - Gap detection (incoming `seq > lastSeq + 1` → `RESUME`).
- **Optimistic send:** generate a `clientMsgId` (UUID v4) and render the message as `pending`. On `ACK`, replace it with the server `messageId/seq`. On `NACK`/timeout it becomes `failed` with a retry button (same id). The pending queue is flushed after reconnect.
- **Rendering:** markdown via `react-markdown` + `rehype-sanitize` (no raw HTML). Media via a presigned GET URL.

## Tasks
- [ ] **P5-T01** Scaffold `frontend/`:
  - Vite + React + TS (strict), ESLint, Prettier, Tailwind, shadcn/ui, Vitest + RTL + MSW.
  - Pin `typescript` to `~6`: TS 7 has no `tsserver`, so the `typescript-lsp` plugin fails (P0-T10). Recheck if the plugin supports TS 7 by then.
  - Re-enable `typescript-lsp` in `.claude/settings.json` `enabledPlugins` (disabled in P0-T12).
  - Vite proxy.
  - `npm run` scripts: `dev`, `build`, `lint`, `typecheck`, `test`, `gen:api`.
- [ ] **P5-T02** `gen:api` from `/v3/api-docs` → `src/api/schema.d.ts`. CI check that the generated file is up to date
- [ ] **P5-T03** Auth pages (login, register), in-memory token, refresh wrapper with single-flight, route guard, logout
- [ ] **P5-T04** Layout: sidebar channel list (unread badge, last message preview, presence dot for DMs), create channel/DM dialog
- [ ] **P5-T05** Message view: react-virtuoso reverse infinite scroll, day separators, edited/deleted states, reply preview, reactions
- [ ] **P5-T06** `ws/` client: state machine, ticket, heartbeat, backoff, typed protocol, event bus → Query cache/stores. Unit tests with a fake WebSocket
- [ ] **P5-T07** Composer: optimistic send, pending/failed states, edit/delete, reply, emoji reactions, typing emit (throttled)
- [ ] **P5-T08** Media: presigned PUT upload with a progress bar, image/video preview, size and type checks client-side
- [ ] **P5-T09** Presence and typing UI; `READ` on scroll-to-bottom / visibility; read receipts in small channels
- [ ] **P5-T10** Connection banner ("Reconnecting…"), `RESYNC_REQUIRED` handling, `RECONNECT` frame handling
- [ ] **P5-T11** Search box (keyword search, P3-T13)
- [ ] **P5-T12** Ask DevCool panel (P8c): streamed answer, clickable `[msg:id]` citations that jump to the message, cancel button
- [ ] **P5-T13** Playwright E2E: two browser contexts, log in as two users, send, receive, see typing, reload and see history. Run in CI against docker compose
- [ ] **P5-T14** `frontend-deploy.yml`: build → S3 sync → CloudFront invalidation. The CI frontend job runs lint/typecheck/test/build on path changes
- [ ] **P5-T15** Enable the `frontend-design` and `playwright` plugins in `.claude/settings.json`; add a `frontend/CLAUDE.md` section on component conventions

## Files touched
`frontend/**`, `.github/workflows/{ci,frontend-deploy}.yml`, `.claude/settings.json`

## Test plan
- **Vitest:** WS state machine transitions, pending queue flush, dedupe/order merge into the cache, refresh single-flight.
- **MSW:** REST mocks for page tests.
- **Playwright:** the two-user flow (T13).

## Definition of Done
Milestone **M3** (with P7): the deployed SPA shows every P3/P4 feature. Killing an ECS task shows the reconnect banner, then recovers with no duplicate or missing messages.

## Interview talking points
- Client half of delivery: pending queue + idempotency key + resume + gap detection.
- Why tokens live in memory and the refresh token in an HttpOnly cookie. XSS vs CSRF trade-offs.
- Merging push events into a paginated cache without duplicates.

## Risks
- Scope creep in UI polish. Timebox; the backend is the showcase.
- The generated API types drift if the backend changes without regenerating. The CI check catches it.
