# ADR-003: A shared `common` library, with a deliberately narrow scope

## Status

Accepted.

## Context

Several concerns are genuinely identical across services: the OAuth2 request/response DTOs
exchanged with `auth-service`, the error-response shape every API returns, the Kafka event
contracts the purchase-flow services all publish/consume. Without somewhere to put them, each
service either duplicates them (and drifts out of sync) or calls back to another service just to
get a shared type.

## Options considered

1. **Duplicate the shared types in every service.** No coupling, but the OAuth2 DTOs, the JWT
   claim-reading logic, and the Kafka event schemas would all need to change in lockstep by hand
   across five-to-nine copies, exactly the drift this system's `common` module exists to avoid.
2. **One large shared library with anything reusable.** Tempting, but turns `common` into a
   dumping ground and a hidden coupling point; a change "for" one service can silently break
   every other consumer.
3. **A small shared library with an explicit, narrow scope** (the option taken): cross-cutting
   DTOs/events, the JWT-claim-reading utility, the S3 storage helper, and the shared error
   handler. Business logic stays in the owning service.

## Decision

`common` holds: `dto/AuthRequest`+`AuthResponse` (the OAuth2 shapes every service that talks to
`auth-service` needs), `event/*` (the five purchase-flow event classes plus `Topics`, so producer
and consumer always agree on the wire format), `util/JwtUtil` (reading the `user_name` claim off
an authenticated request, since every resource server needs the exact same logic here), `util/
CoreFeignConfiguration` (Feign form-encoding, needed by every Feign client that posts to the
OAuth2 token endpoint), `aws/ProfilePictureStorage` (the one piece of infrastructure code two
services genuinely share), and `exception/CustomizedResponseEntityExceptionHandler` (a uniform
error-response shape, `{timestamp, message, details}`, across every service's API).

**Deliberately not in `common`**: any JPA entity, any business rule, any service-specific
DTO. `auth-service` defines its *own* `MemberEntity` (id/email/password hash only - just what
authentication needs) rather than sharing one with any other service that might touch a member
record, on purpose: coupling two services' internal data models to each other's release cycles
for no real benefit. This system used to also have `member-service`, with its own richer
`MemberEntity` mapped to the same `members` table with a different column subset for
profile/social-network features - since removed as out of scope for the purchase-order flow this
repo now focuses on, but the same reasoning would apply again if a service like it returned.

## Consequences

- **Good**: the OAuth2 wire format and the five Kafka event contracts can only drift if someone
  edits `common` and forgets to rebuild every dependent module, a build-time failure, not a
  silent runtime mismatch.
- **Bad, honestly**: `common` has no automated boundary enforcement (no architecture test, e.g.
  ArchUnit, asserting "no JPA entity ever lands here"). The boundary is a convention documented
  here and in `CLAUDE.md`, not something the build fails on if violated.
- **Bad, honestly**: every module depending on `common` must rebuild and reinstall it locally
  before building against a change, since there's no reactor POM to sequence that automatically
  (see `CLAUDE.md`'s Commands section). A larger team would want a proper multi-module Maven
  reactor or a published artifact repository instead of `mvn install`-to-the-local-repo.
