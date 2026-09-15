# ADR-002: OAuth2 password grant + JWT for authentication, with a role-based access model

## Status

Accepted.

## Context

Every service needs to know who's calling and what they're allowed to do, without each one
independently managing sessions or talking to a shared session store (which would reintroduce
the coupling microservices are meant to avoid). Clients are a browser/mobile app calling through
`api-gateway`, not a server-rendered app that would naturally use cookies.

## Options considered

1. **Server-side sessions + a shared session store (e.g. Redis).** Familiar, but couples every
   service to that store's availability and adds a stateful dependency the rest of the
   architecture (stateless, horizontally-scalable services) is designed to avoid.
2. **API keys per client.** Simple, but doesn't represent *which user* is acting, only which
   client application is, the wrong shape for a per-member social network.
3. **OAuth2 password grant issuing a signed JWT** (the option taken). `auth-service` is the
   authorization server; every other service is a resource server that verifies the same JWT
   independently, with no shared state and no call back to `auth-service` per request.

## Decision

`auth-service` authenticates a member's credentials, issues a JWT signed with an RSA private
key, and every resource server verifies it with the corresponding public key
(`SessionCreationPolicy.STATELESS` everywhere, so authorization is entirely re-derived from the
token each request). Roles are granted authorities baked into the token: `NORMAL_USER` gets
`ROLE_MEMBER`, `PREMIUM_USER` additionally gets `ROLE_PREMIUMMEMBER`, `ADMIN` additionally gets
`ROLE_ADMIN`, though only `NORMAL_USER` is ever actually assigned today, so the tiers exist as a
designed extension point, not a currently-exercised feature. `api-gateway` forwards
username/password to `auth-service` on the client's behalf so the browser never sees the OAuth2
client secret.

## Consequences

- **Good**: no service depends on a shared session store; any resource server can verify a token
  entirely offline once it has the public key. Adding a new protected service (see
  `resource-service`, deliberately kept as a template) means wiring in the same public key and
  `@EnableResourceServer`, with no new coupling to `auth-service`.
- **Bad, honestly**: revoking a single token before its expiry isn't possible without a
  denylist. A stateless JWT is valid until it expires, full stop. This system doesn't implement
  one; a real deployment would need a short token lifetime plus refresh tokens, or a denylist
  checked at the gateway, to bound the blast radius of a leaked token.
- **Bad, honestly**: the same RSA keypair is a demo literal, duplicated as the fallback in every
  service's `application.properties` (see the Config & secrets note in `CLAUDE.md`).
  A real deployment would generate a keypair per environment and distribute it via a secrets
  manager, not a checked-in default.
- The password grant type itself is deprecated in OAuth 2.1 guidance (it exposes the user's raw
  credentials to the client application). It's kept here because `api-gateway`, not a
  third-party client, is the only thing that ever sees the raw password, and it never persists
  or logs it; a public third-party client would need authorization code + PKCE instead.
