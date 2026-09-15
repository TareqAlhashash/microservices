package com.investorbook.invoiceservice.service;

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
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.invoiceservice.dao.InvoiceRepository;
import com.investorbook.invoiceservice.dao.ProcessedEventRepository;
import com.investorbook.invoiceservice.dao.entities.Invoice;
import com.investorbook.invoiceservice.dao.entities.ProcessedEvent;

@Component
public class InvoiceEventListener {

	private static final Logger logger = LoggerFactory.getLogger(InvoiceEventListener.class);

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
			logger.info("ignoring redelivered PaymentSucceeded event {}", event.getEventId());
			return;
		}

		String invoiceNumber = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		Instant issuedAt = Instant.now();
		invoiceRepository.save(new Invoice(UUID.randomUUID().toString(), event.getOrderId(), invoiceNumber,
				event.getAmount(), issuedAt));

		kafkaTemplate.send(Topics.INVOICE_ISSUED, event.getOrderId(), new InvoiceIssued(UUID.randomUUID().toString(),
				event.getOrderId(), event.getCustomerEmail(), event.getAmount(), invoiceNumber, issuedAt));
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
}
