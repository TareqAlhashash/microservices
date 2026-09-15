package com.investorbook.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by payment-service when the (mocked) payment for an order is
 * declined. order-service consumes this to run its compensating action -
 * cancelling the order - which is what makes this flow a saga rather than
 * just a happy-path event chain.
 */
public class PaymentFailed {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private BigDecimal amount;
	private String reason;
	private Instant occurredAt;

	public PaymentFailed() {
		super();
	}

	public PaymentFailed(String eventId, String orderId, String customerEmail, BigDecimal amount, String reason,
			Instant occurredAt) {
		this.eventId = eventId;
		this.orderId = orderId;
		this.customerEmail = customerEmail;
		this.amount = amount;
		this.reason = reason;
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

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}
}
