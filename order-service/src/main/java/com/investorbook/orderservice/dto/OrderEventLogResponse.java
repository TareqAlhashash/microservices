package com.investorbook.orderservice.dto;

import java.time.Instant;

import com.investorbook.orderservice.dao.entities.OrderEventLogEntity;

public class OrderEventLogResponse {

	private String eventType;
	private String message;
	private Instant occurredAt;

	public OrderEventLogResponse() {
		super();
	}

	public OrderEventLogResponse(String eventType, String message, Instant occurredAt) {
		this.eventType = eventType;
		this.message = message;
		this.occurredAt = occurredAt;
	}

	public static OrderEventLogResponse from(OrderEventLogEntity entity) {
		return new OrderEventLogResponse(entity.getEventType(), entity.getMessage(), entity.getOccurredAt());
	}

	public String getEventType() {
		return eventType;
	}

	public String getMessage() {
		return message;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
