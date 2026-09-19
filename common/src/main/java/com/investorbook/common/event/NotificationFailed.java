package com.investorbook.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by notification-service when the customer cannot be notified at all (a
 * permanent failure such as an undeliverable address, not a transient SMTP outage).
 * invoice-service consumes this to void the invoice it already issued (compensating
 * for InvoiceIssued).
 */
public class NotificationFailed {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private BigDecimal amount;
	private String invoiceNumber;
	private String reason;
	private Instant occurredAt;

	public NotificationFailed() {
		super();
	}

	public NotificationFailed(String eventId, String orderId, String customerEmail, BigDecimal amount,
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
