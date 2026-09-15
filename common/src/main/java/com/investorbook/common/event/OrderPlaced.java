package com.investorbook.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by order-service when a new order is persisted (status PLACED).
 * Carries what payment-service needs to act without calling back to
 * order-service - see the purchase-flow ADR for why (and the trade-off of
 * denormalizing customerEmail/amount into every downstream event too).
 */
public class OrderPlaced {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private BigDecimal amount;
	private Instant occurredAt;

	public OrderPlaced() {
		super();
	}

	public OrderPlaced(String eventId, String orderId, String customerEmail, BigDecimal amount, Instant occurredAt) {
		this.eventId = eventId;
		this.orderId = orderId;
		this.customerEmail = customerEmail;
		this.amount = amount;
		this.occurredAt = occurredAt;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public void setCustomerEmail(String customerEmail) {
		this.customerEmail = customerEmail;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}
}
