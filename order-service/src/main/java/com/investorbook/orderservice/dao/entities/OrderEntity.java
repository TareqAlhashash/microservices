package com.investorbook.orderservice.dao.entities;

import java.math.BigDecimal;
import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "orders")
public class OrderEntity {

	@Id
	@Column(name = "id")
	private String id;

	@Column(name = "customer_email")
	private String customerEmail;

	@Column(name = "amount")
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private OrderStatus status;

	@Column(name = "created_at")
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	public OrderEntity() {
		super();
	}

	public OrderEntity(String id, String customerEmail, BigDecimal amount, OrderStatus status, Instant createdAt) {
		this.id = id;
		this.customerEmail = customerEmail;
		this.amount = amount;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = createdAt;
	}

	public String getId() {
		return id;
	}

	public String getCustomerEmail() {
		return customerEmail;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public void setStatus(OrderStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
