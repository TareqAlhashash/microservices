# ADR-006: Kafka, not a simple queue, for the purchase-flow events

## Status

Accepted.

## Context

The purchase flow needs to move facts (`OrderPlaced`, `PaymentSucceeded`, and so on) from one
service to another reliably, with `order-service` additionally needing to consume three different
event types to track its own status.

## Options considered

1. **A simple message queue** (e.g. RabbitMQ, SQS, ActiveMQ). Lighter to run and operate,
   point-to-point or fanout delivery, messages are typically consumed once and gone.
2. **Kafka** (the option taken). A durable, replayable log per topic, multiple independent
   consumer groups can each read the full history at their own pace, ordering is guaranteed
   within a partition.

## Decision

Kafka, with one topic per event type (`order.placed`, `payment.succeeded`, `payment.failed`,
`invoice.issued`, `order.completed`), each partitioned by order id so every event for a given
order lands in the same partition and keeps its relative order. This saga needs per-order
ordering, not a global one, so a single partition per topic would work too at this scale, but the
key-based partitioning is what a real multi-partition deployment would actually need.

## Consequences

- **Good**: durability and replay are real here, not theoretical. If `notification-service` is
  down for an hour, `invoice.issued` events simply wait in the topic; no message is lost the way
  it could be with an at-most-once queue with no dead-letter handling. Multiple consumers could
  independently read the same topic for different purposes (e.g. an analytics service consuming
  `order.placed` without affecting `payment-service`'s consumption) with zero code change to the
  producer.
- **Bad, honestly, the trade-off worth stating plainly**: Kafka is heavier than this problem
  strictly needs. Running a broker (even the single-node KRaft setup in this repo's
  `docker-compose.yml`) is more operational surface than a queue would be, and four services
  each doing simple point-to-point handoffs don't inherently need a distributed log's replay
  or multi-consumer-group guarantees. A simple queue would have been *sufficient* for exactly
  this four-step chain as it stands today.
- **Why Kafka anyway**: the point of building this flow was to demonstrate the concepts a queue
  doesn't force you to confront as directly: partitioned ordering, consumer groups, at-least-
  once delivery and the idempotency it demands (see ADR-007), and replay. A queue would have
  hidden most of that behind simpler semantics. If this flow's actual throughput/replay/fan-out
  needs stayed this modest in a real system, revisiting this decision in favor of a lighter queue
  would be a reasonable, defensible call.
