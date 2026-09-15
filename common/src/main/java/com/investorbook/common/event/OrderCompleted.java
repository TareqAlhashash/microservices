package com.investorbook.common.event;

import java.time.Instant;

/**
 * Published by notification-service once it has sent (mocked/logged) the
 * completion email - the terminal event of a successful saga. order-service
 * consumes this to move the order to its final COMPLETED status.
 */
public class OrderCompleted {

	private String eventId;
	private String orderId;
	private String customerEmail;
	private Instant occurredAt;

	public OrderCompleted() {
		super();
	}

	public OrderCompleted(String eventId, String orderId, String customerEmail, Instant occurredAt) {
		this.eventId = eventId;
		this.orderId = orderId;
		this.customerEmail = customerEmail;
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

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public void setOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}
}
