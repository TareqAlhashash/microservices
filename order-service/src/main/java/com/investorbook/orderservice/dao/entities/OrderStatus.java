package com.investorbook.orderservice.dao.entities;

/**
 * PLACED -&gt; PAID -&gt; INVOICED -&gt; COMPLETED is the happy path; PLACED -&gt;
 * PAYMENT_FAILED is the saga's compensating path (see OrderEventListener).
 * Each transition is only applied from its expected predecessor status - a
 * duplicate/redelivered event that would repeat an already-applied
 * transition is a no-op, which is this service's idempotency strategy.
 */
public enum OrderStatus {
	PLACED, PAID, INVOICED, COMPLETED, PAYMENT_FAILED
}
