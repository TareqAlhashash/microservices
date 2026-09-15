package com.investorbook.orderservice.service;

import java.math.BigDecimal;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

public class PlaceOrderRequest {

	@NotNull(message = "amount cannot be null")
	@DecimalMin(value = "0.01", message = "amount must be positive")
	private BigDecimal amount;

	public PlaceOrderRequest() {
		super();
	}

	public PlaceOrderRequest(BigDecimal amount) {
		this.amount = amount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}
}
