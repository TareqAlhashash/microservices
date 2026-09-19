package com.investorbook.notificationservice.service;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.NotificationFailed;
import com.investorbook.common.event.OrderCompleted;
import com.investorbook.common.event.Topics;
import com.investorbook.notificationservice.dao.ProcessedEventRepository;
import com.investorbook.notificationservice.dao.entities.ProcessedEvent;

@Component
public class NotificationEventListener {

	private static final Logger logger = LoggerFactory.getLogger(NotificationEventListener.class);

	private final ProcessedEventRepository processedEventRepository;
	private final NotificationEmailSender emailSender;
	private final KafkaTemplate<String, Object> kafkaTemplate;

	public NotificationEventListener(ProcessedEventRepository processedEventRepository,
			NotificationEmailSender emailSender, KafkaTemplate<String, Object> kafkaTemplate) {
		this.processedEventRepository = processedEventRepository;
		this.emailSender = emailSender;
		this.kafkaTemplate = kafkaTemplate;
	}

	@KafkaListener(topics = Topics.INVOICE_ISSUED, groupId = "notification-service")
	@Transactional
	public void onInvoiceIssued(InvoiceIssued event) {
		if (alreadyProcessed(event.getEventId())) {
			logger.info("ignoring redelivered InvoiceIssued event {}", sanitizeForLog(event.getEventId()));
			return;
		}

		String safeOrderId = sanitizeForLog(event.getOrderId());
		logger.info("saga: InvoiceIssued for order {} (invoice {}), notifying the customer",
				safeOrderId, sanitizeForLog(event.getInvoiceNumber()));

		// A mail-server problem is a best-effort side channel, not a gate on the
		// saga completing: no real mail server is configured outside tests (see
		// NotificationEmailSender), so failing to send shouldn't leave every order
		// stuck at INVOICED forever in a normal local run. An address that can
		// never receive mail is different: nothing can ever be sent, so that one
		// is a real failure and compensates the saga.
		try {
			emailSender.sendCompletionEmail(event);
		} catch (UndeliverableRecipientException e) {
			publishNotificationFailed(event, e.getMessage());
			return;
		} catch (Exception e) {
			logger.warn("failed to send completion email for order {}: {}", safeOrderId,
					sanitizeForLog(e.toString()));
		}

		logger.info("saga: customer notified for order {}, publishing OrderCompleted", safeOrderId);
		kafkaTemplate.send(Topics.ORDER_COMPLETED, event.getOrderId(),
				new OrderCompleted(UUID.randomUUID().toString(), event.getOrderId(), event.getCustomerEmail(),
						Instant.now()));
	}

	private void publishNotificationFailed(InvoiceIssued event, String reason) {
		logger.warn("compensation: the customer for order {} can never be notified ({}), invoice {} has to be "
				+ "voided and the payment refunded, publishing NotificationFailed",
				sanitizeForLog(event.getOrderId()), sanitizeForLog(reason), sanitizeForLog(event.getInvoiceNumber()));
		kafkaTemplate.send(Topics.NOTIFICATION_FAILED, event.getOrderId(),
				new NotificationFailed(UUID.randomUUID().toString(), event.getOrderId(), event.getCustomerEmail(),
						event.getAmount(), event.getInvoiceNumber(), reason, Instant.now()));
	}

	/** See payment-service's ProcessedEvent for why this needs saveAndFlush, not save. */
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
