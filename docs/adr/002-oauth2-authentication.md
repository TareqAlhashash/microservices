# ADR-002: Username/password + a signed JWT for authentication, with a role-based access model

## Status

Accepted. Superseded its own earlier form: this system briefly implemented option 3 below (a
real OAuth2 authorization server) before settling on option 4 - see "History" at the end.

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
3. **A real OAuth2 authorization server** (Spring Authorization Server) issuing a signed JWT.
   Spec-correct and what a system with third-party clients, multiple grant types, or consent
   screens would need - none of which this system has. Tried, then dropped: see "History" below.
4. **A plain `/login` endpoint that authenticates the user and signs a JWT directly** (the option
   taken). `auth-service` is not an OAuth2 provider of any kind; every other service is still a
   resource server that verifies the same JWT independently, with no shared state and no call
   back to `auth-service` per request - everything OAuth2 would have bought this system, without
   the machinery a system with exactly one first-party client doesn't need.

## Decision

`auth-service` authenticates a member's credentials (`LoginController`/`MemberDetailsService`/
BCrypt) and signs a JWT directly with Nimbus JOSE (`JwtIssuer`) using an RSA private key; every
resource server verifies it with the corresponding public key (`SessionCreationPolicy.STATELESS`
everywhere, so authorization is entirely re-derived from the token each request). Roles are
granted authorities baked into the token: `NORMAL_USER` gets `ROLE_MEMBER`, `PREMIUM_USER`
additionally gets `ROLE_PREMIUMMEMBER`, `ADMIN` additionally gets `ROLE_ADMIN`, though only
`NORMAL_USER` is ever actually assigned today, so the tiers exist as a designed extension point,
not a currently-exercised feature. `api-gateway` forwards username/password to `auth-service` on
the client's behalf as plain JSON - there is no OAuth2 client, so there is no client secret for
the browser to be kept away from either.

## Consequences

- **Good**: no service depends on a shared session store; any resource server can verify a token
  entirely offline once it has the public key. Adding a new protected service (see
  `order-service`'s `SecurityConfiguration`) means wiring in the same public key and a resource
  server dependency, with no new coupling to `auth-service`. This system used to keep a
  deliberately skeletal `resource-service` module purely as a template for that, since removed
  as out of scope for the purchase-order flow this repo now focuses on.
- **Good**: no OAuth2 framework, no client registry, no grant types, no token
  introspection/revocation endpoints - the whole issuing side is two small classes
  (`LoginController`, `JwtIssuer`). Every one of those OAuth2 concepts would have been solving a
  problem this system (one first-party client, one grant shape) doesn't have.
- **Bad, honestly**: revoking a single token before its expiry isn't possible without a
  denylist. A stateless JWT is valid until it expires, full stop. This system doesn't implement
  one, and doesn't implement a refresh-token flow to bound the exposure either - a token is valid
  for `investorbook.security.jwt.expiration` seconds (24h by default) and re-authenticating is
  what happens after that. A real deployment would need a short token lifetime plus refresh
  tokens, or a denylist checked at the gateway, to bound the blast radius of a leaked token.
- **Bad, honestly**: the same RSA keypair is a demo literal, duplicated as the fallback in every
  service's `application.properties` (see the Config & secrets note in `CLAUDE.md`).
  A real deployment would generate a keypair per environment and distribute it via a secrets
  manager, not a checked-in default.

## History: the OAuth2 authorization server this system tried first

`auth-service` was originally rewritten onto Spring Authorization Server (the successor to
Spring Security OAuth2's removed `@EnableAuthorizationServer`) when the rest of this repo moved
off Spring Boot 2.2.13. That framework turned out not to implement the OAuth2 password grant at
all - deliberately, since it was dropped in OAuth 2.1 guidance (it exposes the user's raw
credentials to the client application) - but this system's login flow (api-gateway's `/login`,
the React frontend) sends username/password directly and expects a token back, i.e. password
grant. Rather than redesign the login flow around a redirect-based Authorization Code exchange,
password grant was added back as a genuine custom grant-type extension
(`PasswordGrantAuthenticationToken`/`Converter`/`Provider`, mirroring the framework's own
`OAuth2ClientCredentialsAuthenticationProvider`) - a real, working implementation, not a
workaround.

It was also solving a much bigger problem than this system actually has: an OAuth2 authorization
server exists to serve potentially many clients (first- and third-party), multiple grant types,
consent screens, and token introspection/revocation - this system has exactly one first-party
client (api-gateway, on the browser's behalf). Once that mismatch was named directly, the
custom-grant-extension approach was replaced with option 4 above: the framework was dropped
entirely in favour of `auth-service` authenticating the user and signing its own JWT, which is
the *entire* problem this system actually has. Nothing downstream changed - `JwtIssuer`
reproduces the exact same `user_name`/`authorities` claim shape the OAuth2 path used to produce,
so every resource server's JWT *verification* code was untouched by either rewrite.
