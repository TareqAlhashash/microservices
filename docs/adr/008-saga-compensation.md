# ADR-008: A real compensating action on payment failure, not just a happy-path event chain

## Status

Accepted. Extended by [ADR-010](010-compensation-after-payment.md), which adds compensation for the
steps after payment. The "only one compensating action" reasoning below describes the flow as it
was when this was written.

## Context

There's no ACID boundary across `order-service`, `payment-service`, `invoice-service`, and
`notification-service`; an order is committed in `order-service`'s database before payment is
even attempted. If payment fails, that commit can't be rolled back the way a single-database
transaction could; the order already exists as `PLACED`.

## Options considered

1. **Do nothing on failure, just don't emit the success events.** The order would sit at
   `PLACED` forever with no signal to the customer or any operator that anything went wrong. Not
   a saga, just an incomplete happy path.
2. **A compensating action**: on `PaymentFailed`, `order-service` transitions the order to a
   terminal `PAYMENT_FAILED` status (the option taken), an explicit, visible undo of the
   `PLACED` state, even though nothing else needed undoing yet at this point in the flow.

## Decision

`payment-service` publishes `PaymentFailed` (with a `reason`) instead of `PaymentSucceeded` when
its mocked, deterministic check declines an order. `order-service`'s `OrderEventListener`
consumes it via the same state-machine-guard mechanism as every other transition
(`PLACED -> PAYMENT_FAILED`, only applied if the order is still `PLACED`); this is the saga's one
and only compensating action, and `OrderServiceApiIT.aFailedPayment_cancelsTheOrder` proves it
runs, not just that the code exists.

## Consequences

- **Good**: an interviewer's first question about this flow, "what happens when payment fails
  after the order is placed?", has a real, tested answer: the order visibly reaches a terminal
  failure state, not silence.
- **Honestly, why this saga only needed one compensating action**: nothing else in the flow had
  committed a side effect yet by the time payment fails; no invoice, no email, no downstream
  state anywhere else to undo. That's a property of *this* saga's ordering (payment is the first
  and only gate before anything else happens), not a general truth about sagas. A saga where a
  later step could fail *after* an earlier step had already produced a real side effect (e.g. if
  invoice generation could fail after payment already succeeded) would need a real compensating
  action at that point too, e.g. issuing a refund event, which this flow doesn't have to
  contend with today because `invoice-service`'s and `notification-service`'s own steps are
  designed not to fail in ways that need undoing (notification failure in particular is
  deliberately treated as best-effort, not a gate; see `NotificationEventListener`).
- **Bad, honestly**: `PAYMENT_FAILED` is purely informational from the system's side; nothing
  currently notifies the customer that their order failed (only successful orders trigger
  `notification-service`). A more complete implementation would publish a failure notification
  too, reusing the same "email is best-effort" pattern already in place for success.
