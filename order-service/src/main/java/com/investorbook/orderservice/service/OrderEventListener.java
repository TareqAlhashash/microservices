package com.investorbook.orderservice.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.OrderCompleted;
import com.investorbook.common.event.PaymentFailed;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.orderservice.dao.OrderRepository;
import com.investorbook.orderservice.dao.entities.OrderEntity;
import com.investorbook.orderservice.dao.entities.OrderStatus;

/**
 * Drives the order's own status through the saga as the terminal events for
 * each step arrive. Idempotency here is a state-machine guard rather than a
 * separate dedupe table (contrast payment-service/invoice-service/
 * notification-service, which have no other state to guard on): each
 * transition only applies from its expected predecessor status, so a
 * redelivered event that would repeat an already-applied transition is a
 * silent no-op instead of double-processing.
 */
@Component
public class OrderEventListener {

	private static final Logger logger = LoggerFactory.getLogger(OrderEventListener.class);

	private final OrderRepository orderRepository;

	public OrderEventListener(OrderRepository orderRepository) {
		this.orderRepository = orderRepository;
	}

	@KafkaListener(topics = Topics.PAYMENT_SUCCEEDED, groupId = "order-service")
	@Transactional
	public void onPaymentSucceeded(PaymentSucceeded event) {
		transitionIfExpected(event.getOrderId(), OrderStatus.PLACED, OrderStatus.PAID);
	}

	@KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "order-service")
	@Transactional
	public void onPaymentFailed(PaymentFailed event) {
		transitionIfExpected(event.getOrderId(), OrderStatus.PLACED, OrderStatus.PAYMENT_FAILED);
	}

	@KafkaListener(topics = Topics.INVOICE_ISSUED, groupId = "order-service")
	@Transactional
	public void onInvoiceIssued(InvoiceIssued event) {
		transitionIfExpected(event.getOrderId(), OrderStatus.PAID, OrderStatus.INVOICED);
	}

	@KafkaListener(topics = Topics.ORDER_COMPLETED, groupId = "order-service")
	@Transactional
	public void onOrderCompleted(OrderCompleted event) {
		transitionIfExpected(event.getOrderId(), OrderStatus.INVOICED, OrderStatus.COMPLETED);
	}

	private void transitionIfExpected(String orderId, OrderStatus expectedCurrent, OrderStatus next) {
		OrderEntity order = orderRepository.findById(orderId).orElse(null);
		if (order == null) {
			logger.warn("received an event for unknown order {}", sanitizeForLog(orderId));
			return;
		}
		if (order.getStatus() != expectedCurrent) {
			logger.info("ignoring duplicate/out-of-order transition for order {}: expected {} but was {}",
					sanitizeForLog(orderId), expectedCurrent, order.getStatus());
			return;
		}
		order.setStatus(next);
		order.setUpdatedAt(Instant.now());
		orderRepository.save(order);
	}

	// Strips CR/LF so a Kafka message's order id (attacker-controllable if a producer
	// were ever compromised) can't forge extra log lines or corrupt log-file structure.
	private static String sanitizeForLog(String value) {
		return value == null ? null : value.replaceAll("[\r\n]", "_");
	}
}
