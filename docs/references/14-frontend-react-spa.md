# 14 — Frontend: React SPA with a realtime client

> **Used in DevCool:** [Phase 5](../plans/phases/phase-5-frontend.md) (P5-T01–T15) · [02 — Tech stack, Frontend](../plans/architecture/02-tech-stack.md#frontend) · [03 §10](../plans/architecture/03-chat-system-design.md#10-reconnect-and-resume) (client state machine) · [ADR-0011](../plans/architecture/adr/0011-ws-ticket-auth-single-origin.md) (Vite proxy, in-memory token) · P8-T11, T15 (search toggle, Ask panel)
> **Also see:** [frontend/CLAUDE.md](../../frontend/CLAUDE.md)
> **Links checked:** 2026-09-29

## Concepts to own

- **Server state vs client state.** Server data (channels, messages) lives in TanStack Query's cache; ephemeral UI/connection state (socket status, presence, typing) lives in a small Zustand store. Don't copy server data into the store.
- **Pushing WS events into the query cache.** `queryClient.setQueryData` to append/patch messages from `MESSAGE_NEW`/`MESSAGE_UPDATED`, or `invalidateQueries` when a patch is too complex. Dedupe by `messageId`/`seq`.
- **Infinite queries in reverse.** History pages load older messages upward (`beforeSeq`); the virtualized list must keep the scroll position when items are prepended.
- **Optimistic send.** Show the message immediately with a pending state and `clientMsgId`, reconcile on `ACK`, mark failed on `NACK`/timeout, retry with the same id.
- **The WS client as a state machine.** Connecting → Open → Backoff → Draining → Closed, driven by close codes and `RECONNECT` frames; fetch a fresh ticket before each connect; resubscribe + `RESUME` + flush pending on open.
- **Typed protocol with discriminated unions.** `type` as the discriminant gives exhaustive `switch` handling of server frames in TypeScript.
- **Generated API types.** `openapi-typescript` from `/v3/api-docs` + `openapi-fetch`; CI fails when the generated file is stale.
- **Tokens in the browser.** Access token in memory only; refresh via the `HttpOnly` cookie; a single-flight refresh so N parallel 401s trigger one refresh.
- **Rendering untrusted content.** Markdown rendered without raw HTML and sanitized; no `dangerouslySetInnerHTML` on user text.
- **Testing layers.** Vitest + React Testing Library for components, MSW for REST (and WebSocket) mocking, Playwright with two browser contexts for the send/receive E2E.

## Read first

1. [TanStack Query — Overview](https://tanstack.com/query/latest/docs/framework/react/overview) and [Infinite Queries](https://tanstack.com/query/latest/docs/framework/react/guides/infinite-queries) — *official docs* · The cache model and bidirectional paging (`getPreviousPageParam`).
2. [Using WebSockets with React Query](https://tkdodo.eu/blog/using-web-sockets-with-react-query) — *TkDodo (TanStack Query maintainer)* · Invalidate vs `setQueryData` for pushed events. The pattern for P5-T06.
3. [Optimistic Updates](https://tanstack.com/query/latest/docs/framework/react/guides/optimistic-updates) — *official docs* · Rollback on error; maps onto pending/failed messages (P5-T07).
4. [openapi-fetch](https://openapi-ts.dev/openapi-fetch/) — *official docs* · Typed fetch client from the generated schema (P5-T02).
5. [Playwright — Browser contexts](https://playwright.dev/docs/browser-contexts) — *official docs* · Two isolated users in one test (P5-T13).

## Reference

### React, Vite, TypeScript

- [React docs](https://react.dev/) — *official docs* · Start with "Thinking in React" and "Escape Hatches".
- [React 19 release notes](https://react.dev/blog/2024/12/05/react-19) — *React blog* · Actions, `useOptimistic`, ref as prop.
- [You Might Not Need an Effect](https://react.dev/learn/you-might-not-need-an-effect) — *React docs* · Avoid effect-driven data flows around the socket.
- [useSyncExternalStore](https://react.dev/reference/react/useSyncExternalStore) — *React docs* · How to subscribe components to an external source like a WS connection store.
- [Vite guide](https://vite.dev/guide/) and [server.proxy](https://vite.dev/config/server-options.html#server-proxy) — *official docs* · Proxy `/api` and `/ws` (with `ws: true`) to `localhost:8080` for single-origin local dev.
- [TypeScript — Narrowing and discriminated unions](https://www.typescriptlang.org/docs/handbook/2/narrowing.html#discriminated-unions) — *official docs* · Typing the WS frame union.

### State and routing

- [Updates from mutation responses (`setQueryData`)](https://tanstack.com/query/latest/docs/framework/react/guides/updates-from-mutation-responses) — *official docs* · Immutable cache updates.
- [Practical React Query](https://tkdodo.eu/blog/practical-react-query) — *TkDodo* · The blog series index; read "Effective React Query Keys" and "React Query and forms" too.
- [Zustand docs](https://zustand.docs.pmnd.rs/) — *official docs* · Stores, selectors, and using a store outside React (the WS client).
- [React Router](https://reactrouter.com/) — *official docs* · Route guards via loaders or wrapper components.

### UI and long lists

- [Tailwind CSS docs](https://tailwindcss.com/docs) — *official docs*.
- [shadcn/ui](https://ui.shadcn.com/docs) — *official docs* · Copy-in components built on Radix.
- [Radix Primitives](https://www.radix-ui.com/primitives/docs/overview/introduction) — *official docs* · Accessibility behaviour behind dialogs, menus, popovers.
- [React Virtuoso — endless scrolling](https://virtuoso.dev/react-virtuoso/virtuoso/endless-scrolling/) and [initial index / top items](https://virtuoso.dev/react-virtuoso/virtuoso/initial-index/) — *official docs* · Loading more and starting at the bottom. Read the [API reference](https://virtuoso.dev/react-virtuoso/api-reference/common/) for `firstItemIndex` (prepend without jumping) and `followOutput` (stick to bottom on new messages). The separate [Virtuoso Message List](https://virtuoso.dev/message-list/) is a commercial component built for chat.

### Generated API client

- [openapi-typescript](https://openapi-ts.dev/) — *official docs* · Generating `schema.d.ts` from `/v3/api-docs`.

### Browser APIs used by the chat UI

- [Page Visibility API](https://developer.mozilla.org/en-US/docs/Web/API/Page_Visibility_API) — *MDN* · Send `READ` only when the tab is visible (P5-T09).
- [XMLHttpRequest.upload](https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest/upload) — *MDN* · Upload progress for the presigned PUT (`fetch` has no upload progress events).
- [react-markdown](https://github.com/remarkjs/react-markdown) — *README* · Safe-by-default markdown rendering (no raw HTML unless enabled).
- [DOMPurify](https://github.com/cure53/DOMPurify) — *README* · If HTML ever has to be rendered, sanitize it.

### Testing

- [Vitest guide](https://vitest.dev/guide/) — *official docs*.
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/) — *official docs* · Query by role/text, not implementation details.
- [Common mistakes with React Testing Library](https://kentcdodds.com/blog/common-mistakes-with-react-testing-library) — *Kent C. Dodds* · Practical habits.
- [Mock Service Worker](https://mswjs.io/docs/) — *official docs* · REST mocking in tests.
- [MSW — Mocking WebSocket](https://mswjs.io/docs/websocket/) — *official docs* · MSW can intercept WebSocket connections too, which may replace the "small fake server" the tech-stack doc expects.
- [Playwright — Intro](https://playwright.dev/docs/intro) and [Web server](https://playwright.dev/docs/test-webserver) — *official docs* · Starting the app for E2E in CI.
- [Playwright — Authentication](https://playwright.dev/docs/auth) — *official docs* · Reusing login state across tests.

### Project structure

- [Bulletproof React](https://github.com/alan2207/bulletproof-react) — *GitHub* · A widely used feature-folder layout for React apps.

## Self-check

- Where does a message live after `MESSAGE_NEW` arrives, and how do you avoid showing it twice when the `ACK` for your own send also arrives?
- How does the list keep its scroll position when an older page is prepended?
- Walk through the client state machine on a `RECONNECT {afterMs: 4000}` frame.
- Why fetch a new ticket for every reconnect, and what happens if the ticket request itself fails?
- Why keep the access token in memory, and what does the user experience on page reload?
- Design the single-flight refresh: three requests get 401 at once. How many refresh calls happen?
- What's the difference between invalidating a query and calling `setQueryData` on a WS event? When is each right?
