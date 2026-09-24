package com.investorbook.orderservice.service;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.investorbook.orderservice.dao.OrderEventLogRepository;
import com.investorbook.orderservice.dao.entities.OrderEventLogEntity;

/**
 * Persists one audit row per saga event so the dashboard can show an order's event
 * timeline. Deliberately a separate, best-effort write from the order's own status
 * transition (see OrderEventListener): a dashboard's history is a convenience, not part
 * of the saga's correctness, so it should never be why an event fails to process.
 */
@Component
public class OrderEventLogRecorder {

	private final OrderEventLogRepository repository;

	public OrderEventLogRecorder(OrderEventLogRepository repository) {
		this.repository = repository;
	}

	public void record(String orderId, String eventType, String message) {
		repository.save(new OrderEventLogEntity(orderId, eventType, message, Instant.now()));
	}
}
