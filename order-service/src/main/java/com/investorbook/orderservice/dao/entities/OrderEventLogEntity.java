package com.investorbook.orderservice.dao.entities;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A durable audit trail of every saga event touching an order, one row per event, in the
 * order they were observed. Exists so a dashboard can show "what happened to this order"
 * without scraping console log output, which isn't persisted anywhere queryable and
 * wouldn't exist at all in a real deployment (see OrderEventLogRecorder for how rows are
 * written, reusing the same message text each event's own logger.info/warn call already uses).
 */
@Entity
@Table(name = "order_event_logs")
public class OrderEventLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "order_id")
	private String orderId;

	@Column(name = "event_type")
	private String eventType;

	@Column(name = "message")
	private String message;

	@Column(name = "occurred_at")
	private Instant occurredAt;

	public OrderEventLogEntity() {
		super();
	}

	public OrderEventLogEntity(String orderId, String eventType, String message, Instant occurredAt) {
		this.orderId = orderId;
		this.eventType = eventType;
		this.message = message;
		this.occurredAt = occurredAt;
	}

	public Long getId() {
		return id;
	}

	public String getOrderId() {
		return orderId;
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
