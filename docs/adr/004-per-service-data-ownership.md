# ADR-004: Per-service table ownership (on a shared Postgres instance, honestly)

## Status

Accepted, with a known demo-scale gap called out below.

## Context

Microservices are supposed to own their data, with no service reaching into another's tables
directly. But this repo also needs to be runnable on a single laptop with one `mvn
spring-boot:run` per service and no infrastructure beyond one Postgres instance.

## Options considered

1. **One database, one schema, shared tables, any service reads/writes any table.** Fastest to
   stand up, but recreates a single relational monolith underneath a microservices facade, the
   thing this pattern exists to avoid.
2. **One Postgres instance per service.** The textbook answer, and what a real deployment should
   do, but multiplies local setup from "one Postgres" to "nine," for no benefit at demo scale
   where nothing here needs independent scaling or failure isolation.
3. **One shared Postgres instance, but every service owns its own tables exclusively** (the
   option taken): `auth-service`/`member-service` each have their own `MemberEntity` mapped to
   the same `members` table with different column subsets (see ADR-003) but neither ever
   queries a table it doesn't own; each purchase-flow service has its own tables (`orders`,
   `processed_events`, one instance per service, not shared, `invoices`) and the four services
   never query each other's tables at all, only exchange Kafka events.

## Decision

Ownership is enforced at the **table** level, not the **instance** level: every service's JPA
repositories only ever touch tables that service itself defined. Cross-service data needs are
met by an event payload carrying what's needed (see ADR-003's note on denormalized event fields)
or, for the original REST-based services, a fresh HTTP call, never a direct query into another
service's tables.

## Consequences

- **Good**: no service can be broken by another service's schema migration, and the boundary
  that actually matters for independent deployability (data ownership) is real, even though the
  physical database is shared.
- **Bad, honestly, and worth saying plainly**: this is a demo-scale compromise, not the
  production answer. One shared Postgres instance is a single point of failure and a scaling
  bottleneck across every service that uses it, and nothing stops a future contributor from
  accidentally querying another service's table. There's no database-level permission boundary
  enforcing what this ADR describes, only convention. A real deployment would give each service
  (or at least each bounded context, `auth`+`member` arguably share one closely enough to
  debate) its own database instance or schema with actual grants restricting cross-access.
- The purchase-flow services could have shared a single `processed_events` table with a
  `service_name` discriminator column instead of one per service; four separate tables were
  chosen so each service's idempotency state is trivially its own migration to own, move, or
  drop independently later.
