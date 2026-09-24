package com.investorbook.orderservice.dao.entities;

/**
 * PLACED -&gt; PAID -&gt; INVOICED -&gt; COMPLETED is the happy path. Two terminal
 * failure states exist, one per kind of compensation (see OrderEventListener):
 * PLACED -&gt; PAYMENT_FAILED when payment is declined (nothing was charged, so
 * nothing to undo), and PLACED/PAID/INVOICED -&gt; CANCELLED once a step after
 * payment has failed and payment-service has refunded the customer.
 * Each transition is only applied from its expected predecessor status - a
 * duplicate/redelivered event that would repeat an already-applied
 * transition is a no-op, which is this service's idempotency strategy.
 */
public enum OrderStatus {
	PLACED, PAID, INVOICED, COMPLETED, PAYMENT_FAILED, CANCELLED
}
