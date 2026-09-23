package com.investorbook.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.hateoas.server.core.Relation;

import com.investorbook.orderservice.dao.entities.OrderEntity;

/**
 * @Relation names the _embedded key PagedModel&lt;OrderResponse&gt; serializes this collection
 * under (see OrderController.listOrders) - without it, spring-hateoas derives one from this
 * class's simple name ("orderResponseList"), tying a wire-format detail to a Java type name a
 * client shouldn't need to know about.
 */
@Relation(collectionRelation = "orders")
public class OrderResponse {

	private String id;
	private String customerEmail;
	private BigDecimal amount;
	private String status;
	private Instant createdAt;

	public OrderResponse() {
		super();
	}

	public OrderResponse(String id, String customerEmail, BigDecimal amount, String status, Instant createdAt) {
		this.id = id;
		this.customerEmail = customerEmail;
		this.amount = amount;
		this.status = status;
		this.createdAt = createdAt;
	}

	public static OrderResponse from(OrderEntity order) {
		return new OrderResponse(order.getId(), order.getCustomerEmail(), order.getAmount(),
				order.getStatus().name(), order.getCreatedAt());
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

	public String getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
