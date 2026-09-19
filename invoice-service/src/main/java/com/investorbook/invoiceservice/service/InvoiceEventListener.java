package com.investorbook.invoiceservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.investorbook.common.event.InvoiceFailed;
import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.InvoiceVoided;
import com.investorbook.common.event.NotificationFailed;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.invoiceservice.dao.InvoiceRepository;
import com.investorbook.invoiceservice.dao.ProcessedEventRepository;
import com.investorbook.invoiceservice.dao.entities.Invoice;
import com.investorbook.invoiceservice.dao.entities.ProcessedEvent;

/**
 * Issues an invoice for every paid order, and voids it again if a later step
 * (notifying the customer) fails. Like payment's approval rule, the invoicing
 * rule is a mocked, deterministic stand-in: amounts at or above INVOICE_LIMIT
 * can't be invoiced automatically (simulating a manual tax-review
 * requirement), which is the saga's step-two failure. It sits below payment's
 * own decline threshold on purpose, so a payment can succeed and the invoice
 * still fail, which is exactly the case that needs a refund.
 */
@Component
public class InvoiceEventListener {

	private static final Logger logger = LoggerFactory.getLogger(InvoiceEventListener.class);

	static final BigDecimal INVOICE_LIMIT = new BigDecimal("500.00");

	private final ProcessedEventRepository processedEventRepository;
	private final InvoiceRepository invoiceRepository;
	private final KafkaTemplate<String, Object> kafkaTemplate;

	public InvoiceEventListener(ProcessedEventRepository processedEventRepository,
			InvoiceRepository invoiceRepository, KafkaTemplate<String, Object> kafkaTemplate) {
		this.processedEventRepository = processedEventRepository;
		this.invoiceRepository = invoiceRepository;
		this.kafkaTemplate = kafkaTemplate;
	}

	@KafkaListener(topics = Topics.PAYMENT_SUCCEEDED, groupId = "invoice-service")
	@Transactional
	public void onPaymentSucceeded(PaymentSucceeded event) {
		if (alreadyProcessed(event.getEventId())) {
			logger.info("ignoring redelivered PaymentSucceeded event {}", sanitizeForLog(event.getEventId()));
			return;
		}

		if (event.getAmount().compareTo(INVOICE_LIMIT) >= 0) {
			publishInvoiceFailed(event);
			return;
		}

		// Locale.ROOT: uppercasing hex characters in a generated identifier, not
		// user-facing text - locale-independent on purpose.
		String invoiceNumber = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
		Instant issuedAt = Instant.now();
		invoiceRepository.save(new Invoice(UUID.randomUUID().toString(), event.getOrderId(), invoiceNumber,
				event.getAmount(), issuedAt));

		logger.info("saga: PaymentSucceeded for order {}, issued invoice {}, publishing InvoiceIssued",
				sanitizeForLog(event.getOrderId()), invoiceNumber);
		kafkaTemplate.send(Topics.INVOICE_ISSUED, event.getOrderId(), new InvoiceIssued(UUID.randomUUID().toString(),
				event.getOrderId(), event.getCustomerEmail(), event.getAmount(), invoiceNumber, issuedAt));
	}

	@KafkaListener(topics = Topics.NOTIFICATION_FAILED, groupId = "invoice-service")
	@Transactional
	public void onNotificationFailed(NotificationFailed event) {
		if (alreadyProcessed(event.getEventId())) {
			logger.info("ignoring redelivered NotificationFailed event {}", sanitizeForLog(event.getEventId()));
			return;
		}

		String safeOrderId = sanitizeForLog(event.getOrderId());
		String safeInvoiceNumber = sanitizeForLog(event.getInvoiceNumber());
		logger.warn("compensation: NotificationFailed for order {} ({}), invoice {} has to be voided",
				safeOrderId, sanitizeForLog(event.getReason()), safeInvoiceNumber);

		Optional<Invoice> invoice = invoiceRepository.findByOrderId(event.getOrderId());
		if (invoice.isPresent() && invoice.get().isVoided()) {
			logger.info("compensation: invoice {} for order {} was already voided, InvoiceVoided was sent "
					+ "earlier so not sending it again", safeInvoiceNumber, safeOrderId);
			return;
		}

		if (invoice.isPresent()) {
			invoice.get().markVoided(Instant.now());
			invoiceRepository.save(invoice.get());
		} else {
			// The payment still has to be refunded, so the chain continues regardless.
			logger.warn("compensation: no invoice on file for order {}, nothing to void, continuing so the "
					+ "payment is still refunded", safeOrderId);
		}

		kafkaTemplate.send(Topics.INVOICE_VOIDED, event.getOrderId(),
				new InvoiceVoided(UUID.randomUUID().toString(), event.getOrderId(), event.getCustomerEmail(),
						event.getAmount(), event.getInvoiceNumber(), event.getReason(), Instant.now()));
		logger.info("compensation: invoice {} for order {} voided, void invoice sent (InvoiceVoided) so "
				+ "payment-service refunds the payment", safeInvoiceNumber, safeOrderId);
	}

	private void publishInvoiceFailed(PaymentSucceeded event) {
		String reason = "amount " + event.getAmount().toPlainString() + " meets or exceeds the $"
				+ INVOICE_LIMIT.toPlainString() + " automatic invoicing limit";
		logger.warn("compensation: PaymentSucceeded for order {} but no invoice can be issued ({}), the "
				+ "payment has to be refunded, publishing InvoiceFailed", sanitizeForLog(event.getOrderId()),
				sanitizeForLog(reason));
		kafkaTemplate.send(Topics.INVOICE_FAILED, event.getOrderId(), new InvoiceFailed(UUID.randomUUID().toString(),
				event.getOrderId(), event.getCustomerEmail(), event.getAmount(), reason, Instant.now()));
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
