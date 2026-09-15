package com.investorbook.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Published by invoice-service after generating an invoice for a paid order. */
public class InvoiceIssued {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private BigDecimal amount;
	private String invoiceNumber;
	private Instant occurredAt;

	public InvoiceIssued() {
		super();
	}

	public InvoiceIssued(String eventId, String orderId, String customerEmail, BigDecimal amount,
			String invoiceNumber, Instant occurredAt) {
		this.eventId = eventId;
		this.orderId = orderId;
		this.customerEmail = customerEmail;
		this.amount = amount;
		this.invoiceNumber = invoiceNumber;
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

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}
}
