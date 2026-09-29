# 03 — WebSocket and the realtime protocol

> **Used in DevCool:** [ADR-0005](../plans/architecture/adr/0005-raw-websocket-protocol-v2.md) (raw WS, typed envelope v2) · [ADR-0011](../plans/architecture/adr/0011-ws-ticket-auth-single-origin.md) (tickets, origin check) · [03 §5, §6, §10](../plans/architecture/03-chat-system-design.md#5-the-websocket-protocol) · [01 §4.5](../plans/architecture/01-system-architecture.md#45-deploy-with-graceful-websocket-drain) (graceful drain) · P4-T01–T03, T06, T09, T14 · P5-T06, T10
> **Already in the repo:** [learning/10](../learning/10-realtime-architecture-design.md) · [.claude/rules/websocket-protocol.md](../../.claude/rules/websocket-protocol.md) · the `ws-protocol` skill
> **Links checked:** 2026-09-29

## Concepts to own

- **The handshake is HTTP.** A WebSocket starts as an HTTP/1.1 `GET` with `Upgrade: websocket`. That is why `Origin` checks, handshake interceptors and query-string tickets work, and why the browser `WebSocket` API cannot set an `Authorization` header.
- **Frames and control frames.** Text/binary data frames plus `ping`, `pong` and `close`. Browsers don't expose protocol-level ping, so apps add their own `PING`/`PONG` frames. *In DevCool:* `PING` every 25 s, server idle close after 90 s.
- **Close codes.** 1000 normal, 1001 going away, 1008 policy violation, 1011 server error, 1012 service restart, 1013 try again later; 4000–4999 are for applications. *In DevCool:* 4400 protocol mismatch, 4401 auth, 1012 on deploy, 1013 overload.
- **Application protocol on top.** Raw WS gives you a pipe. The envelope (`v`, `type`, `reqId`, `payload`), ACK/NACK per request, error frames that don't close the socket, and forward compatibility (ignore unknown fields) are yours to design.
- **Backpressure.** A socket's send buffer fills when a client reads slowly. Without a limit, one slow client blocks the sender thread or eats heap. *In DevCool:* `ConcurrentWebSocketSessionDecorator` with a 5 s send-time limit, 512 KB buffer and `TERMINATE`.
- **Heartbeats and idle timeouts.** Every hop (ALB, CloudFront, NAT, proxies) has an idle timeout. The heartbeat interval must be shorter than the smallest one.
- **Reconnect with exponential backoff and full jitter.** `sleep = random(0, min(cap, base · 2^attempt))`. Without jitter, a fleet-wide disconnect becomes a synchronized reconnect storm.
- **Graceful drain.** On deploy: readiness goes down, clients get `RECONNECT {afterMs: jitter}`, sockets close with 1012, clients resume on other tasks.
- **Ticket authentication.** A short-lived, single-use ticket obtained over normal bearer-auth REST, then passed as `?ticket=`. Nothing durable leaks into URLs or logs.
- **Cross-site WebSocket hijacking (CSWSH).** Browsers send cookies on cross-origin WS handshakes and there is no CORS for WebSockets. The server must check `Origin`.
- **Alternatives.** STOMP (destinations and a broker model), SSE + POST (plain HTTP, built-in `Last-Event-ID` resume), managed services. Know why ADR-0005 rejected each.

## Read first

1. [RFC 6455 — The WebSocket Protocol](https://www.rfc-editor.org/rfc/rfc6455.html) — *IETF spec* · Read §1.3 (opening handshake), §5.5 (control frames: ping/pong/close) and §7.4 (status codes). Skip the framing bit layout on the first pass.
2. [Spring Framework — WebSockets](https://docs.spring.io/spring-framework/reference/6.2/web/websocket.html) — *official docs* · The `WebSocketHandler` API, handshake interceptors, allowed origins and server config. This is the API `RawWebSocketHandler` uses.
3. [Exponential Backoff And Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/) — *AWS Architecture Blog* · Why "full jitter", with simulations. The reconnect policy in [03 §10](../plans/architecture/03-chat-system-design.md#10-reconnect-and-resume) comes from here.
4. [WebSocket Security](https://devcenter.heroku.com/articles/websocket-security) — *Heroku Dev Center* · Origin checks and the ticket-based authentication pattern that ADR-0011 adopts.
5. [High Performance Browser Networking — WebSocket](https://hpbn.co/websocket/) — *Ilya Grigorik, free online book chapter* · Framing overhead, head-of-line blocking, proxies, and performance advice.

## Reference

### Specs and registries

- [IANA WebSocket Close Code Number Registry](https://www.iana.org/assignments/websocket#close-code-number) — *registry* · The authoritative list of close codes, including 1012 and 1013 used for drain and overload.
- [WHATWG WebSockets Standard](https://websockets.spec.whatwg.org/) — *browser spec* · The browser-side API: what the `WebSocket` object can and cannot do (no custom headers).
- [HTML Standard — Server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html) — *spec* · The SSE alternative in ADR-0005, including `Last-Event-ID` resume.
- [STOMP 1.2 specification](https://stomp.github.io/stomp-specification-1.2.html) — *spec* · The protocol ADR-0005 rejected; useful to compare with your own envelope.

### Browser API

- [MDN — WebSocket](https://developer.mozilla.org/en-US/docs/Web/API/WebSocket) — *reference* · Events, `readyState`, `bufferedAmount` (client-side backpressure signal).
- [MDN — CloseEvent.code](https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent/code) — *reference* · Close codes as the client sees them; drives the client state machine in P5-T06.
- [MDN — Writing WebSocket servers](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API/Writing_WebSocket_servers) — *guide* · The handshake and framing walked through by hand; the fastest way to understand what Spring does for you.

### Spring server side

- [Spring Framework — WebSocket API (handlers, handshake, server config, origins)](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html) — *official docs* · Handshake interceptors, `setAllowedOrigins`, buffer and timeout settings.
- [`ConcurrentWebSocketSessionDecorator` Javadoc](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/web/socket/handler/ConcurrentWebSocketSessionDecorator.html) — *API docs* · `sendTimeLimit`, `bufferSizeLimit` and `OverflowStrategy`, used in P4-T01.
- [Spring Framework — STOMP over WebSocket](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp.html) — *official docs* · The option not taken; read "Flow of Messages" and "External Broker" to see what a broker relay would add.

### Resilience and operations

- [Timeouts, retries, and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/) — *Amazon Builders' Library* · Retry budgets and why retries amplify outages.
- [Backpressure explained — the resisted flow of data through software](https://medium.com/@jayphelps/backpressure-explained-the-flow-of-data-through-software-2350b3e77ce7) — *Jay Phelps* · The concept in general terms: buffer, drop, or control the producer.
- ALB idle timeout and CloudFront WebSocket behaviour: see [07 — AWS compute, edge and networking](07-aws-compute-edge-networking.md).

### Security

- [OWASP WebSocket Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html) — *OWASP* · Origin validation, authentication, message validation, rate limiting.
- [Cross-site WebSocket hijacking](https://portswigger.net/web-security/websockets/cross-site-websocket-hijacking) — *PortSwigger Web Security Academy* · The attack the origin allowlist in ADR-0011 blocks, with a lab.

### Books

- *High Performance Browser Networking* (Ilya Grigorik) — ch. 17 WebSocket, ch. 16 SSE. Free at hpbn.co.
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- Why can't a browser client send `Authorization: Bearer` on the upgrade request, and what are the four alternatives ADR-0011 compared?
- What is the difference between a protocol-level ping frame and the app-level `PING` frame? Why does DevCool need the latter?
- Which idle timeouts sit between the browser and an `api` task, and how does the 25 s heartbeat relate to them?
- What happens to the sender thread when one recipient stops reading, with and without the session decorator?
- Why does a bad frame get a `NACK` instead of closing the socket?
- Write the full-jitter formula. What goes wrong with plain exponential backoff after a deploy?
- Why does CSWSH work at all when REST endpoints are protected by CORS?
