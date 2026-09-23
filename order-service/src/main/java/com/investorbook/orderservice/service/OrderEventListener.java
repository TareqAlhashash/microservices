package com.investorbook.orderservice.service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.investorbook.common.event.InvoiceFailed;
import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.InvoiceVoided;
import com.investorbook.common.event.NotificationFailed;
import com.investorbook.common.event.OrderCompleted;
import com.investorbook.common.event.PaymentFailed;
import com.investorbook.common.event.PaymentRefunded;
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
 *
 * Two kinds of failure end an order early. A declined payment
 * (PAYMENT_FAILED) needs no undoing, since nothing was charged. Anything
 * that fails after payment is compensated by payment-service refunding the
 * customer, and its PaymentRefunded is what finally cancels the order here.
 *
 * Also listens to InvoiceFailed/NotificationFailed/InvoiceVoided purely to record them
 * on the dashboard's event timeline (see OrderEventLogRecorder) - those three don't
 * change this order's own status (payment-service/invoice-service react to them
 * instead), but they're real waypoints in the compensation chain worth showing.
 */
@Component
public class OrderEventListener {

	private static final Logger logger = LoggerFactory.getLogger(OrderEventListener.class);

	// A refund can be observed before this service has processed the order's own
	// PaymentSucceeded/InvoiceIssued (different topics, no cross-topic ordering),
	// so a cancel is accepted from every status that isn't already terminal.
	private static final Set<OrderStatus> CANCELLABLE = EnumSet.of(OrderStatus.PLACED, OrderStatus.PAID,
			OrderStatus.INVOICED);

	private final OrderRepository orderRepository;
	private final OrderEventLogRecorder eventLogRecorder;

	public OrderEventListener(OrderRepository orderRepository, OrderEventLogRecorder eventLogRecorder) {
		this.orderRepository = orderRepository;
		this.eventLogRecorder = eventLogRecorder;
	}

	@KafkaListener(topics = Topics.PAYMENT_SUCCEEDED, groupId = "order-service")
	@Transactional
	public void onPaymentSucceeded(PaymentSucceeded event) {
		logger.info("saga: PaymentSucceeded for order {}, payment captured, marking the order PAID and "
				+ "waiting for invoice-service", sanitizeForLog(event.getOrderId()));
		eventLogRecorder.record(event.getOrderId(), "PaymentSucceeded",
				"saga: PaymentSucceeded, payment captured, marking the order PAID and waiting for invoice-service");
		transitionIfExpected(event.getOrderId(), EnumSet.of(OrderStatus.PLACED), OrderStatus.PAID);
	}

	@KafkaListener(topics = Topics.PAYMENT_FAILED, groupId = "order-service")
	@Transactional
	public void onPaymentFailed(PaymentFailed event) {
		logger.warn("compensation: PaymentFailed for order {} ({}), the payment was declined so no funds were "
				+ "captured and nothing has to be refunded, marking the order PAYMENT_FAILED",
				sanitizeForLog(event.getOrderId()), sanitizeForLog(event.getReason()));
		eventLogRecorder.record(event.getOrderId(), "PaymentFailed", "compensation: PaymentFailed ("
				+ event.getReason() + "), the payment was declined, marking the order PAYMENT_FAILED");
		transitionIfExpected(event.getOrderId(), EnumSet.of(OrderStatus.PLACED), OrderStatus.PAYMENT_FAILED);
	}

	@KafkaListener(topics = Topics.INVOICE_ISSUED, groupId = "order-service")
	@Transactional
	public void onInvoiceIssued(InvoiceIssued event) {
		logger.info("saga: InvoiceIssued for order {} (invoice {}), marking the order INVOICED and waiting for "
				+ "notification-service", sanitizeForLog(event.getOrderId()),
				sanitizeForLog(event.getInvoiceNumber()));
		eventLogRecorder.record(event.getOrderId(), "InvoiceIssued",
				"saga: InvoiceIssued (invoice " + event.getInvoiceNumber()
						+ "), marking the order INVOICED and waiting for notification-service");
		transitionIfExpected(event.getOrderId(), EnumSet.of(OrderStatus.PAID), OrderStatus.INVOICED);
	}

	@KafkaListener(topics = Topics.ORDER_COMPLETED, groupId = "order-service")
	@Transactional
	public void onOrderCompleted(OrderCompleted event) {
		logger.info("saga: OrderCompleted for order {}, the customer was notified, marking the order COMPLETED "
				+ "(end of the saga)", sanitizeForLog(event.getOrderId()));
		eventLogRecorder.record(event.getOrderId(), "OrderCompleted",
				"saga: OrderCompleted, the customer was notified, marking the order COMPLETED (end of the saga)");
		transitionIfExpected(event.getOrderId(), EnumSet.of(OrderStatus.INVOICED), OrderStatus.COMPLETED);
	}

	@KafkaListener(topics = Topics.PAYMENT_REFUNDED, groupId = "order-service")
	@Transactional
	public void onPaymentRefunded(PaymentRefunded event) {
		logger.warn("compensation: PaymentRefunded for order {} ({}), the customer has been refunded, so the "
				+ "order is cancelled (end of the compensation chain)", sanitizeForLog(event.getOrderId()),
				sanitizeForLog(event.getReason()));
		eventLogRecorder.record(event.getOrderId(), "PaymentRefunded",
				"compensation: PaymentRefunded (" + event.getReason()
						+ "), the customer has been refunded, order cancelled");
		transitionIfExpected(event.getOrderId(), CANCELLABLE, OrderStatus.CANCELLED);
	}

	@KafkaListener(topics = Topics.INVOICE_FAILED, groupId = "order-service")
	public void onInvoiceFailed(InvoiceFailed event) {
		logger.warn("compensation: InvoiceFailed for order {} ({}), payment-service will refund the customer",
				sanitizeForLog(event.getOrderId()), sanitizeForLog(event.getReason()));
		eventLogRecorder.record(event.getOrderId(), "InvoiceFailed",
				"compensation: InvoiceFailed (" + event.getReason() + "), payment-service will refund the customer");
	}

	@KafkaListener(topics = Topics.NOTIFICATION_FAILED, groupId = "order-service")
	public void onNotificationFailed(NotificationFailed event) {
		logger.warn("compensation: NotificationFailed for order {} ({}), invoice-service will void the invoice",
				sanitizeForLog(event.getOrderId()), sanitizeForLog(event.getReason()));
		eventLogRecorder.record(event.getOrderId(), "NotificationFailed",
				"compensation: NotificationFailed (" + event.getReason() + "), invoice-service will void the "
						+ "invoice");
	}

	@KafkaListener(topics = Topics.INVOICE_VOIDED, groupId = "order-service")
	public void onInvoiceVoided(InvoiceVoided event) {
		logger.warn("compensation: InvoiceVoided for order {} (invoice {}), payment-service will refund the "
				+ "customer", sanitizeForLog(event.getOrderId()), sanitizeForLog(event.getInvoiceNumber()));
		eventLogRecorder.record(event.getOrderId(), "InvoiceVoided", "compensation: InvoiceVoided (invoice "
				+ event.getInvoiceNumber() + "), payment-service will refund the customer");
	}

	private void transitionIfExpected(String orderId, Set<OrderStatus> expectedCurrent, OrderStatus next) {
		OrderEntity order = orderRepository.findById(orderId).orElse(null);
		if (order == null) {
			logger.warn("received an event for unknown order {}", sanitizeForLog(orderId));
			return;
		}
		if (!expectedCurrent.contains(order.getStatus())) {
			logger.info("ignoring duplicate/out-of-order transition for order {}: expected one of {} but was {}",
					sanitizeForLog(orderId), expectedCurrent, order.getStatus());
			return;
		}
		OrderStatus previous = order.getStatus();
		order.setStatus(next);
		order.setUpdatedAt(Instant.now());
		orderRepository.save(order);
		logger.info("order {} moved {} -> {}", sanitizeForLog(orderId), previous, next);
	}

	// Strips CR/LF so a Kafka message's order id (attacker-controllable if a producer
	// were ever compromised) can't forge extra log lines or corrupt log-file structure.
	private static String sanitizeForLog(String value) {
		return value == null ? null : value.replaceAll("[\r\n]", "_");
	}
}
