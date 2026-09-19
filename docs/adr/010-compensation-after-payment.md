# ADR-010: Compensating actions for every saga step after payment

## Status

Accepted. Extends [ADR-008](008-saga-compensation.md).

## Context

ADR-008 gave the saga one compensating action, for a declined payment, and noted why that was
enough at the time: nothing had produced a side effect yet when payment failed. It also named
the gap: once a step *after* payment can fail, the customer has been charged and something has
to give the money back. Until now `invoice-service` and `notification-service` were designed
never to fail, so a failure there either could not happen or was swallowed, and an order could
sit at `PAID` or `INVOICED` forever with the customer charged.

## Options considered

1. **Keep the steps infallible.** Honest for a demo, but it leaves the saga's hardest question
   ("what if a later step fails after money moved?") unanswered.
2. **A central orchestrator** that calls each service and issues undo calls on failure. Rejected
   for the same reasons as in [ADR-005](005-event-driven-choreography.md): it reintroduces the
   coupling the choreography avoids.
3. **Choreographed compensation** (the option taken): each failing step publishes a failure
   event, and the service that owns the effect to undo reacts to it, in reverse order of the
   original steps.

## Decision

Two new failure points, each with a compensation chain that ends the same way:

```
invoice-service fails    InvoiceFailed -----------------------------> payment-service refunds --PaymentRefunded--> order CANCELLED
notification fails       NotificationFailed --> invoice-service voids --InvoiceVoided--> payment-service refunds --PaymentRefunded--> order CANCELLED
```

- `payment-service` is the single refund point: it consumes `InvoiceFailed` and `InvoiceVoided`
  and publishes `PaymentRefunded`.
- `order-service` consumes `PaymentRefunded` and moves the order to a new terminal `CANCELLED`
  status. `PAYMENT_FAILED` stays as it was: nothing was charged, so nothing is refunded.
- Both new failure points are deterministic and mocked, like the payment decline rule:
  `invoice-service` cannot invoice an amount at or above `INVOICE_LIMIT` ($500.00, simulating a
  manual tax-review requirement), which sits below payment's $1000.00 decline threshold so that
  payment can succeed and the invoice still fail. `notification-service` fails only when the
  customer's address can never receive mail (missing or malformed), signalled by a dedicated
  `UndeliverableRecipientException`.
- A mail server being down is still best-effort, exactly as before: the error is logged and the
  order completes. The distinction is retryability. An outage might clear, an undeliverable
  address never will, so only the second is a saga failure.
- Every consumer keeps its idempotency mechanism: the new `payment-service` and `invoice-service`
  listeners use the `processed_events` dedupe table, and `order-service` uses its state-machine
  guard. `invoice-service` voids by setting a nullable `voided_at` on the invoice, and skips
  publishing again for an invoice that is already voided.
- `order-service` accepts the cancel from `PLACED`, `PAID`, or `INVOICED`, not just from the one
  status the happy path would normally be in. The refund travels through several topics while
  the order's own `PaymentSucceeded` and `InvoiceIssued` arrive on others, and Kafka guarantees
  no ordering across topics, so the cancel can legitimately arrive first. The reverse race is
  safe too: a late `PaymentSucceeded` or `InvoiceIssued` finds the order `CANCELLED`, which is
  not its expected predecessor, and is ignored.
- Every listener now logs what the stage means and what has to happen next (for example
  "payment has to be refunded", "payment refunded", "void invoice sent"), with a
  `compensation:` prefix on the undo path and `saga:` on the forward path, so one order's whole
  story can be followed in the logs.

## Consequences

- **Good**: the saga now answers "what if a later step fails after the customer was charged?"
  with a tested chain, and every failure ends in a terminal, visible state (`CANCELLED`) rather
  than a stuck one. Each link is proven in its own service's suite; no single test spins up four
  deployables in one JVM.
- **Bad, honestly**: the refund is a stand-in. `payment-service` keeps no ledger of charges, so it
  cannot check that a payment being refunded ever existed, and there is no call to a real payment
  provider.
- **Bad, honestly**: nothing handles a compensation that itself fails. There are no retries with
  backoff and no dead-letter topic, so a refund that errors would leave the order un-cancelled.
  A production system needs both, plus alerting on stuck sagas.
- **Bad, honestly**: `CANCELLED` records that the order was undone but not why. The reason travels
  in `PaymentRefunded` and appears in the logs, but `order-service` does not store it.
- **Bad, honestly**: the customer is still not told their order was cancelled or refunded. Only
  successful orders trigger `notification-service`; a failure notification is the same gap
  ADR-008 already named.
- **Bad, honestly**: an unreachable customer cancelling an already-paid order is a debatable
  business rule, chosen here because it is the only failure `notification-service` has that
  retrying cannot fix. A real system might hold the order and ask the customer for a valid
  address instead.
