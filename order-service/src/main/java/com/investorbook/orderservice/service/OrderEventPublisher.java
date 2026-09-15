package com.investorbook.orderservice.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.investorbook.common.event.OrderPlaced;
import com.investorbook.common.event.Topics;
import com.investorbook.orderservice.dao.entities.OrderEntity;

@Component
public class OrderEventPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	public OrderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	/**
	 * Keyed by order id so every event for this order lands in the same
	 * partition and keeps its relative order - this saga only needs
	 * per-order ordering, not a global one.
	 */
	public void publishOrderPlaced(OrderEntity order) {
		OrderPlaced event = new OrderPlaced(UUID.randomUUID().toString(), order.getId(), order.getCustomerEmail(),
				order.getAmount(), Instant.now());
		kafkaTemplate.send(Topics.ORDER_PLACED, order.getId(), event);
	}
}
