# Architecture Decision Records

One page each: Status, Context, Options considered, Decision, Consequences (including the honest
downsides). These are demo-scale decisions, not claims of a production-perfect system.

| ADR | Decision |
|---|---|
| [001](001-service-discovery-and-gateway.md) | Eureka + an API gateway, not direct service calls |
| [002](002-oauth2-authentication.md) | OAuth2 password grant + JWT, with a role-based access model |
| [003](003-shared-common-library.md) | A shared `common` library, with a deliberately narrow scope |
| [004](004-per-service-data-ownership.md) | Per-service table ownership on a shared Postgres instance |
| [005](005-event-driven-choreography.md) | Choreography, not orchestration, for the purchase saga |
| [006](006-kafka-vs-queue.md) | Kafka, not a simple queue, for the purchase-flow events |
| [007](007-idempotency-strategy.md) | Idempotent consumers, via two mechanisms depending on available state |
| [008](008-saga-compensation.md) | A real compensating action on payment failure |
