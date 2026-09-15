package com.investorbook.orderservice.service;

import java.math.BigDecimal;

import com.investorbook.orderservice.dao.entities.OrderEntity;

public class OrderResponse {

	private String id;
	private BigDecimal amount;
	private String status;

	public OrderResponse() {
		super();
	}

	public OrderResponse(String id, BigDecimal amount, String status) {
		this.id = id;
		this.amount = amount;
		this.status = status;
	}

	public static OrderResponse from(OrderEntity order) {
		return new OrderResponse(order.getId(), order.getAmount(), order.getStatus().name());
	}

	public String getId() {
		return id;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getStatus() {
		return status;
	}
}
