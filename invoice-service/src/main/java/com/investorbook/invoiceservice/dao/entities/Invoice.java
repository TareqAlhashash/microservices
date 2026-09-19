package com.investorbook.invoiceservice.dao.entities;

import java.math.BigDecimal;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "invoices")
public class Invoice {

	@Id
	@Column(name = "id")
	private String id;

	@Column(name = "order_id")
	private String orderId;

	@Column(name = "invoice_number")
	private String invoiceNumber;

	@Column(name = "amount")
	private BigDecimal amount;

	@Column(name = "issued_at")
	private Instant issuedAt;

	// Nullable on purpose: null means the invoice is still valid, and it keeps rows that
	// predate this column valid under ddl-auto=update.
	@Column(name = "voided_at")
	private Instant voidedAt;

	public Invoice() {
		super();
	}

	public Invoice(String id, String orderId, String invoiceNumber, BigDecimal amount, Instant issuedAt) {
		this.id = id;
		this.orderId = orderId;
		this.invoiceNumber = invoiceNumber;
		this.amount = amount;
		this.issuedAt = issuedAt;
	}

	public String getId() {
		return id;
	}

	public String getOrderId() {
		return orderId;
	}

	public String getInvoiceNumber() {
		return invoiceNumber;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public boolean isVoided() {
		return voidedAt != null;
	}

	public Instant getVoidedAt() {
		return voidedAt;
	}

	public void markVoided(Instant when) {
		this.voidedAt = when;
	}

	public Instant getIssuedAt() {
		return issuedAt;
	}
}
