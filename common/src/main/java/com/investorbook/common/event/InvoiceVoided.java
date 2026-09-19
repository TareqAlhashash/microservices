package com.investorbook.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by invoice-service after it voids an issued invoice in response to
 * NotificationFailed. payment-service consumes this to refund the payment
 * (compensating for PaymentSucceeded).
 */
public class InvoiceVoided {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private BigDecimal amount;
	private String invoiceNumber;
	private String reason;
	private Instant occurredAt;

	public InvoiceVoided() {
		super();
	}

	public InvoiceVoided(String eventId, String orderId, String customerEmail, BigDecimal amount,
			String invoiceNumber, String reason, Instant occurredAt) {
		this.eventId = eventId;
		this.orderId = orderId;
		this.customerEmail = customerEmail;
		this.amount = amount;
		this.invoiceNumber = invoiceNumber;
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

	public String getInvoiceNumber() {
		return invoiceNumber;
	}

	public void setInvoiceNumber(String invoiceNumber) {
		this.invoiceNumber = invoiceNumber;
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
