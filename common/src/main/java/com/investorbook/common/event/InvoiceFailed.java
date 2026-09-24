package com.investorbook.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by invoice-service when it cannot issue an invoice for an order whose
 * payment already succeeded. payment-service consumes this to refund that payment
 * (compensating for PaymentSucceeded).
 */
public class InvoiceFailed {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private BigDecimal amount;
	private String reason;
	private Instant occurredAt;

	public InvoiceFailed() {
		super();
	}

	public InvoiceFailed(String eventId, String orderId, String customerEmail, BigDecimal amount, String reason,
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
