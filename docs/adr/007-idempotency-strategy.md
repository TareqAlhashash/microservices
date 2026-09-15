# ADR-007: Idempotent consumers, via two different mechanisms depending on available state

## Status

Accepted.

## Context

Kafka delivers at-least-once: any consumer will occasionally see the same message twice (a
rebalance, a retry after a slow commit, a producer retry after an ambiguous ack). Every consumer
in this flow must treat a redelivered event as a no-op; processing `OrderPlaced` twice must not
charge the customer twice or issue two invoices.

## Options considered

1. **An idempotency key on every write, checked and set atomically at the database.** Requires a
   dedicated table per consumer if the consumer has no other natural state to check.
2. **A state-machine guard**: if the consumer already has a durable status field the event would
   transition, only apply the transition when the current state matches the expected
   predecessor. Needs no extra table, but only works for a consumer that already owns such state.
3. **Rely on Kafka consumer-group offset commits alone.** Doesn't actually prevent
   double-processing; a commit can succeed after the business side-effect already happened but
   before the offset is durably recorded, so a crash in that window still redelivers. Not a real
   solution on its own.

## Decision

Both (1) and (2), chosen per consumer based on what state it already has:

- **`order-service`** already has a status field per order; its idempotency mechanism *is* the
  state-machine guard in `OrderEventListener`: each transition only applies from its expected
  predecessor status (e.g. `PAID -> INVOICED` only fires if the order is currently `PAID`); a
  redelivered event finds the order already past that point and is silently ignored. No extra
  table.
- **`payment-service`, `invoice-service`, `notification-service`** have no other durable state to
  guard on; each keeps its own `processed_events` table keyed by event id. The entity
  implements Spring Data's `Persistable` with `isNew()` hardcoded `true`, because a manually-
  assigned `@Id` would otherwise make `save()` merge (update-if-exists) instead of insert,
  silently succeeding on a duplicate instead of surfacing the constraint violation the table
  exists to catch. The insert is flushed immediately, before any Kafka send, since Kafka isn't
  transactional with the database; a message already sent can't be un-sent if the write is
  later found to conflict.

## Consequences

- **Good**: each service's idempotency strategy fits the state it actually has, rather than
  bolting on a dedupe table everywhere out of uniformity. `order-service`'s approach in
  particular needs zero additional schema.
- **Bad, honestly**: the dedupe-table approach only prevents a *duplicate delivery of the same
  event id*; it does nothing about a producer that generates a genuinely new event id for what
  is semantically a repeat of the same action (a bug elsewhere publishing `OrderPlaced` twice for
  one order with two different event ids would still cause a double charge). Idempotency here is
  keyed on the event's identity, not on the business action's identity; a real system with a
  higher bar would key on something like `orderId` directly for `payment-service` specifically,
  since an order should only ever be paid once regardless of how many events claim to place it.
- **Bad, honestly**: no distributed transaction (e.g. an outbox pattern) ties the dedupe-table
  insert to the Kafka publish; they're two separate operations against two separate systems.
  The insert-then-publish ordering minimizes but doesn't eliminate the failure window (a crash
  between the flushed insert and the Kafka send would leave the event marked "processed" with no
  message ever sent). A production system with a stricter delivery guarantee would use a
  transactional outbox table read by a separate relay process instead.
