# 0011 — One-time WebSocket tickets and a single CloudFront origin

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P4 (tickets), P2/P5 (origin)

## Context
`WsAuthHandShakeInterceptor` relies on `JwtAuthFilter` having read `Authorization: Bearer` on the upgrade request. The browser `WebSocket` API cannot set headers, so a browser client can't authenticate today. `/ws` also allows any origin (`setAllowedOrigins("*")`).

Separately, the refresh-token cookie needs a cookie policy that works with the SPA's origin.

## Options considered

### WebSocket authentication
| | Pros | Cons |
|---|---|---|
| Access JWT in the query string (`/ws?token=`) | One step | Long-lived bearer token in URLs → ALB/CloudFront access logs, browser history, Referer |
| JWT in `Sec-WebSocket-Protocol` | Not logged as a URL | Abuses a header meant for subprotocol negotiation; the server must echo it; awkward |
| Cookie on the upgrade request | Automatic | Needs same-site cookies and CSRF-style origin checks; mixes cookie auth into a bearer-token API |
| First-frame auth (`AUTH {token}` after open) | No URL exposure | The socket is open but unauthenticated for a moment; needs a timeout and state machine; the handshake interceptor can't reject early |
| **One-time ticket** (`POST /api/v1/ws/ticket` → `/ws?ticket=`) | Ticket is useless after one use or 30 s, so logs don't leak anything durable. Uses the normal bearer auth to get it. Rejects at handshake | One extra round trip per connect. Needs a shared store (Valkey) so any task can redeem it |

### API origin
| | Pros | Cons |
|---|---|---|
| **Single origin**: CloudFront routes `/*` → S3, `/api/*` and `/ws` → ALB | No CORS. Refresh cookie `SameSite=Strict`. One cert, one WAF | CloudFront in the WS path; needs a no-cache policy for `/api/*` and forwarding of `Authorization`, cookies, query strings |
| Separate `api.` domain | Simpler CloudFront | CORS config; the cookie becomes cross-site, so `SameSite=None` and more CSRF care |

## Decision
1. **Tickets.** `POST /api/v1/ws/ticket` (authenticated) returns `{ticket, expiresIn: 30}`. The ticket is 32 random bytes (base64url), stored in Valkey as `ws:ticket:{t} → userId` with a 30 s TTL. The handshake interceptor redeems it with `GETDEL` (atomic, single-use) and puts `userId` into the session attributes. There's no ticket or an invalid one → reject the handshake with 401.
2. **Origin check** on `/ws`: `setAllowedOrigins(<configured list>)`. Locally `http://localhost:5173`; in AWS the CloudFront domain.
3. **Single origin** through CloudFront, with cache policy `CachingDisabled` and origin request policy `AllViewerExceptHostHeader` for `/api/*` and `/ws`.
4. Refresh cookie `rt`: `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`. This also fixes the current path bug (`/api/v1/auth/refresh` vs `/refresh_token`).

## Consequences
- The frontend WS client fetches a fresh ticket before every (re)connect.
- The ticket redeem path is on every connect. Its latency and failure count become metrics.
- Local dev uses Vite's proxy (`/api`, `/ws` → `localhost:8080`) so the single-origin assumption also holds locally.

## Revisit when
- Native mobile clients arrive: they can send headers, so accept a bearer header as an alternative at the handshake.
