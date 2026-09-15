# ADR-005: Choreography, not orchestration, for the purchase saga

## Status

Accepted.

## Context

Placing an order spans four services (`order-service`, `payment-service`, `invoice-service`,
`notification-service`) with no single database transaction able to span all of them. Something
has to drive the sequence: order placed, then payment attempted, then invoice generated, then
customer notified, then order marked complete (or cancelled, if payment fails).

## Options considered

1. **Orchestration**: a central saga coordinator (either a dedicated service, or an
   orchestration engine like Camunda/Temporal) calls each step in turn, tracks the saga's state
   itself, and issues compensating calls on failure. Gives one place to see the whole flow and
   its state machine, at the cost of a new central component every step depends on.
2. **Choreography** (the option taken): each service reacts only to the event immediately before
   it and publishes the next fact. No service, including `order-service`, knows the full
   chain; `order-service` happens to consume the terminal events too, but only to update its
   *own* status, not to drive the other services.

## Decision

Choreography. `order-service` publishes `OrderPlaced` and moves on; `payment-service` reacts to
it without knowing `order-service` exists as anything but "whoever publishes to `order.placed`";
each subsequent service does the same. The chain is defined by which topics each service
subscribes to and publishes to (see `common`'s `Topics` class), not by any central list of steps.

## Consequences

- **Good**: adding a step (e.g. a fraud-check service between order and payment) means adding a
  new consumer/producer pair, with no existing service's code changing, since none of them know the
  full chain to begin with.
- **Good**: no new component (an orchestrator) to build, deploy, or make highly available. At
  four services, the coordination logic is simple enough that a state machine per event, in each
  consumer, is easier to reason about than a fifth service whose only job is coordinating the
  other four.
- **Bad, honestly**: there is no single place to see "where is order X right now" except
  `order-service`'s own status field, and no single place to see "what does the whole saga do"
  except reading four services' listener code. At more than a handful of steps, or once multiple
  sagas need to interleave or branch conditionally, this becomes genuinely harder to reason about
  than an orchestrator with an explicit state machine, and that crossover point is exactly when this
  decision should be revisited.
- **Bad, honestly**: retry/timeout logic for a stalled saga (e.g. `payment-service` never
  responds) isn't implemented; there's no watchdog noticing an order stuck at `PLACED`. An
  orchestrator would have one natural home for that; here it would need to be a new,
  purpose-built consumer.
