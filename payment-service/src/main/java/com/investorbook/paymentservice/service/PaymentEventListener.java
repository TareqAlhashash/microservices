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

import com.investorbook.common.event.OrderPlaced;
import com.investorbook.common.event.PaymentFailed;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.paymentservice.dao.ProcessedEventRepository;
import com.investorbook.paymentservice.dao.entities.ProcessedEvent;

/**
 * Mocked, deterministic payment decision - the point of this saga is the
 * event-driven orchestration, not a real payment integration. Orders at or
 * above DECLINE_THRESHOLD are declined, simulating a simple risk/fraud
 * threshold; everything else succeeds.
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
			logger.info("ignoring redelivered OrderPlaced event {}", event.getEventId());
			return;
		}

		if (event.getAmount().compareTo(DECLINE_THRESHOLD) < 0) {
			kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, event.getOrderId(),
					new PaymentSucceeded(UUID.randomUUID().toString(), event.getOrderId(), event.getCustomerEmail(),
							event.getAmount(), Instant.now()));
		} else {
			kafkaTemplate.send(Topics.PAYMENT_FAILED, event.getOrderId(),
					new PaymentFailed(UUID.randomUUID().toString(), event.getOrderId(), event.getCustomerEmail(),
							event.getAmount(), "amount meets or exceeds the $1000.00 simulated decline threshold",
							Instant.now()));
		}
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
}
