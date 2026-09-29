# 11 — Application security: authn, authz, tokens, cookies

> **Used in DevCool:** [Phase 1](../plans/phases/phase-1-foundation-hardening.md) (P1-T04–T09: DTO leak, error echo, role checks, cookie, `permitAll`) · [ADR-0011](../plans/architecture/adr/0011-ws-ticket-auth-single-origin.md) (tickets, origin, cookie policy) · [01 §6](../plans/architecture/01-system-architecture.md#6-security-architecture) · [03 §12](../plans/architecture/03-chat-system-design.md#12-security-specifics) · P9-T09 (security pass)
> **Already in the repo:** [learning/02 — Security and authorization](../learning/02-security-and-authorization.md) · [improvements/lessons.md](../improvements/lessons.md) · the `security-reviewer` agent
> **Links checked:** 2026-09-29

## Concepts to own

- **Authenticated is not authorized.** A valid token proves who you are, not what you may touch. Every read/write of channel data checks membership or role in the service layer.
- **BOLA / IDOR.** Iterating `/users/1`, `/users/2` with any valid token. The #1 API risk.
- **Broken object property level authorization.** Returning a domain object leaks every field (password hash, `tokenVersion`). Response DTOs are an allowlist.
- **Error bodies are output too.** Never echo request fields (passwords) or stack traces.
- **Access + refresh tokens.** Short-lived access JWT in memory; refresh token in an `HttpOnly; Secure; SameSite=Strict` cookie scoped to `/api/v1/auth`, rotated on use, with reuse detection. `tokenVersion` revokes all tokens for a user.
- **JWT pitfalls.** Algorithm confusion (`alg: none`, HS/RS mix-ups), missing `exp`/`aud` checks, long lifetimes, tokens in URLs.
- **SameSite and CSRF.** `SameSite=Strict` stops the browser sending the cookie on cross-site requests, which removes most CSRF risk for the refresh endpoint. A single origin makes `Strict` possible.
- **CORS is not access control.** It only relaxes the browser's same-origin policy; WebSockets ignore it entirely (see CSWSH in [03](03-websocket-realtime-protocol.md)).
- **Rate limiting as a security control.** Login, send, typing and AI endpoints; OWASP API4 "unrestricted resource consumption".
- **Secrets and IAM.** No long-lived keys (OIDC in CI, task roles in ECS); task roles scoped to their own secrets.
- **LLM-specific risks.** Prompt injection via retrieved chat content, data leakage across channels. Covered in [13](13-genai-rag-bedrock.md).

## Read first

1. [OWASP API Security Top 10 (2023)](https://api-security.owasp.org/editions/2023/en/0x11-t10/) — *OWASP* · Read API1 (BOLA), API3 (property-level authorization) and API4 (resource consumption) closely; they map onto P1-T04–T06 and the rate limits.
2. [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html) — *OWASP* · Deny by default, check on every request, test authorization logic.
3. [RFC 9700 — Best Current Practice for OAuth 2.0 Security](https://www.rfc-editor.org/rfc/rfc9700.html) — *IETF* · The refresh-token section (sender-constraining or rotation with reuse detection). DevCool isn't OAuth, but the refresh-token rules apply as-is.
4. [JSON Web Token Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html) — *OWASP* · Token sidejacking, revocation, storage on the client, algorithm pinning.
5. [SameSite cookies explained](https://web.dev/articles/samesite-cookies-explained) — *web.dev* · `Strict` vs `Lax` vs `None`, and what "same-site" means.

## Reference

### OWASP

- [API1:2023 Broken Object Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/) — *OWASP* · Attack scenarios and prevention.
- [API3:2023 Broken Object Property Level Authorization](https://api-security.owasp.org/editions/2023/en/0xa3-broken-object-property-level-authorization/) — *OWASP* · Excessive data exposure + mass assignment.
- [API4:2023 Unrestricted Resource Consumption](https://api-security.owasp.org/editions/2023/en/0xa4-unrestricted-resource-consumption/) — *OWASP* · Rate limits and cost limits (the AI token budget).
- [Insecure Direct Object Reference Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html) — *OWASP*.
- [Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html) — *OWASP* · Cookie attributes, rotation, expiry.
- [Cross-Site Request Forgery Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html) — *OWASP* · Where SameSite fits among CSRF defences.
- [Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) — *OWASP* · bcrypt/argon2 parameters; change-password flow (P1-T07).
- [Error Handling Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Error_Handling_Cheat_Sheet.html) — *OWASP* · Generic error bodies (P1-T05).
- [Mass Assignment Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Mass_Assignment_Cheat_Sheet.html) — *OWASP* · Why request DTOs are an allowlist too.
- [REST Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html) — *OWASP* · A checklist for every new endpoint.
- [Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html) — *OWASP* · Rotation, least privilege, CI/CD secrets.
- [OWASP ASVS](https://owasp.org/projects/asvs) — *OWASP* · A verification checklist to structure the P9 security pass.

### Tokens and cookies (standards)

- [RFC 8725 — JSON Web Token Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725.html) — *IETF* · Algorithm verification, audience, explicit typing.
- [RFC 7519 — JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519.html) — *IETF* · The claims (`exp`, `iat`, `aud`, `jti`).
- [RFC 10017 — OAuth 2.0 for Browser-Based Applications](https://www.rfc-editor.org/rfc/rfc10017.html) — *IETF* · Threats to tokens in SPAs and where to keep them (in memory vs storage).
- [MDN — Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie) — *reference* · `Path`, `HttpOnly`, `Secure`, `SameSite`, `__Host-` prefix.
- [Cookies: HTTP State Management Mechanism (RFC 6265bis)](https://datatracker.ietf.org/doc/draft-ietf-httpbis-rfc6265bis/) — *IETF draft* · The spec that defines SameSite.
- [MDN — CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS) — *guide* · Preflights and credentials; what the single origin avoids.
- [Refresh token rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation) — *Auth0 docs* · Reuse detection explained with diagrams.

### Hands-on

- [PortSwigger Web Security Academy — Access control](https://portswigger.net/web-security/access-control) — *free labs* · IDOR and privilege escalation labs.
- [PortSwigger — JWT attacks](https://portswigger.net/web-security/jwt) — *free labs* · Algorithm confusion, `kid` injection, weak secrets.
- [PortSwigger — WebSockets](https://portswigger.net/web-security/websockets) — *free labs* · Message manipulation and CSWSH.

### Edge

- [AWS WAF managed rule groups](https://docs.aws.amazon.com/waf/latest/developerguide/aws-managed-rule-groups.html) — *AWS docs* · The optional WAF in P9-T09.

## Self-check

- Show a request that exploits BOLA on today's `/api/v1/users/{id}`, and the two independent fixes.
- Why does returning a domain object from a controller count as a property-level authorization bug?
- Why is the refresh cookie `Path=/api/v1/auth` and `SameSite=Strict`? What would `SameSite=None` force you to add?
- What is refresh-token reuse detection, and what should the server do when it detects reuse?
- Why does `tokenVersion` let you revoke tokens without a blocklist?
- Where exactly must membership be checked for `SUBSCRIBE`, `RESUME` and `ASK`, and why not in the controller?
- Name three ways a JWT library can be misused to accept a forged token.
