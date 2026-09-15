package com.investorbook.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Published by payment-service after a successful (mocked) payment for an order. */
public class PaymentSucceeded {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private BigDecimal amount;
	private Instant occurredAt;

	public PaymentSucceeded() {
		super();
	}

	public PaymentSucceeded(String eventId, String orderId, String customerEmail, BigDecimal amount,
			Instant occurredAt) {
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
