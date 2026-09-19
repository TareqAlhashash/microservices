package com.investorbook.paymentservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.investorbook.common.event.InvoiceFailed;
import com.investorbook.common.event.InvoiceVoided;
import com.investorbook.common.event.OrderPlaced;
import com.investorbook.common.event.PaymentFailed;
import com.investorbook.common.event.PaymentRefunded;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.paymentservice.dao.ProcessedEventRepository;
import com.investorbook.paymentservice.dao.entities.ProcessedEvent;

/**
 * Mocked, deterministic payment decision - the point of this saga is the
 * event-driven orchestration, not a real payment integration. Orders at or
 * above DECLINE_THRESHOLD are declined, simulating a simple risk/fraud
 * threshold; everything else succeeds.
 *
 * This service is also the saga's single refund point: any failure after a
 * payment succeeded (the invoice could not be issued, or the customer could
 * not be notified and the invoice was voided) ends here as a refund, and the
 * PaymentRefunded it publishes is what lets order-service cancel the order.
 */
@Component
public class PaymentEventListener {

	private static final Logger logger = LoggerFactory.getLogger(PaymentEventListener.class);

	static final BigDecimal DECLINE_THRESHOLD = new BigDecimal("1000.00");

	private final ProcessedEventRepository processedEventRepository;
	private final KafkaTemplate<String, Object> kafkaTemplate;

	public PaymentEventListener(ProcessedEventRepository processedEventRepository,
			KafkaTemplate<String, Object> kafkaTemplate) {
		this.processedEventRepository = processedEventRepository;
		this.kafkaTemplate = kafkaTemplate;
	}

	@KafkaListener(topics = Topics.ORDER_PLACED, groupId = "payment-service")
	@Transactional
	public void onOrderPlaced(OrderPlaced event) {
		if (alreadyProcessed(event.getEventId())) {
			logger.info("ignoring redelivered OrderPlaced event {}", sanitizeForLog(event.getEventId()));
			return;
		}

		if (event.getAmount().compareTo(DECLINE_THRESHOLD) < 0) {
			logger.info("saga: OrderPlaced for order {}, payment of {} captured, publishing PaymentSucceeded",
					sanitizeForLog(event.getOrderId()), event.getAmount());
			kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, event.getOrderId(),
					new PaymentSucceeded(UUID.randomUUID().toString(), event.getOrderId(), event.getCustomerEmail(),
							event.getAmount(), Instant.now()));
		} else {
			logger.warn("saga: OrderPlaced for order {}, payment of {} declined (nothing was captured, so no "
					+ "refund is needed), publishing PaymentFailed", sanitizeForLog(event.getOrderId()),
					event.getAmount());
			kafkaTemplate.send(Topics.PAYMENT_FAILED, event.getOrderId(),
					new PaymentFailed(UUID.randomUUID().toString(), event.getOrderId(), event.getCustomerEmail(),
							event.getAmount(), "amount meets or exceeds the $1000.00 simulated decline threshold",
							Instant.now()));
		}
	}

	@KafkaListener(topics = Topics.INVOICE_FAILED, groupId = "payment-service")
	@Transactional
	public void onInvoiceFailed(InvoiceFailed event) {
		if (alreadyProcessed(event.getEventId())) {
			logger.info("ignoring redelivered InvoiceFailed event {}", sanitizeForLog(event.getEventId()));
			return;
		}

		refund(event.getOrderId(), event.getCustomerEmail(), event.getAmount(),
				"invoice could not be issued: " + event.getReason());
	}

	@KafkaListener(topics = Topics.INVOICE_VOIDED, groupId = "payment-service")
	@Transactional
	public void onInvoiceVoided(InvoiceVoided event) {
		if (alreadyProcessed(event.getEventId())) {
			logger.info("ignoring redelivered InvoiceVoided event {}", sanitizeForLog(event.getEventId()));
			return;
		}

		refund(event.getOrderId(), event.getCustomerEmail(), event.getAmount(),
				"invoice " + event.getInvoiceNumber() + " was voided: " + event.getReason());
	}

	private void refund(String orderId, String customerEmail, BigDecimal amount, String reason) {
		String safeOrderId = sanitizeForLog(orderId);
		String safeReason = sanitizeForLog(reason);
		// FindSecBugs treats any value read from the Kafka event as tainted, BigDecimal included
		String safeAmount = sanitizeForLog(amount.toPlainString());
		logger.warn("compensation: payment of {} for order {} has to be refunded, {}", safeAmount, safeOrderId,
				safeReason);

		// Mocked like the charge itself: a real integration would call the payment
		// provider's refund API here, before announcing the refund.
		kafkaTemplate.send(Topics.PAYMENT_REFUNDED, orderId,
				new PaymentRefunded(UUID.randomUUID().toString(), orderId, customerEmail, amount, reason,
						Instant.now()));

		logger.info("compensation: payment of {} for order {} refunded, published PaymentRefunded so "
				+ "order-service can cancel the order", safeAmount, safeOrderId);
	}

	/**
	 * Forces the INSERT now (not deferred to end-of-transaction) so a
	 * constraint violation on a redelivered event id is caught here, before
	 * any Kafka send - Kafka isn't transactional with this database, so once
	 * a message is sent there's no undoing it.
	 */
	private boolean alreadyProcessed(String eventId) {
		try {
			processedEventRepository.saveAndFlush(new ProcessedEvent(eventId, Instant.now()));
			return false;
		} catch (DataIntegrityViolationException e) {
			return true;
		}
	}

	// Strips CR/LF so a Kafka message's event id (attacker-controllable if a producer
	// were ever compromised) can't forge extra log lines or corrupt log-file structure.
	private static String sanitizeForLog(String value) {
		return value == null ? null : value.replaceAll("[\r\n]", "_");
	}
}
