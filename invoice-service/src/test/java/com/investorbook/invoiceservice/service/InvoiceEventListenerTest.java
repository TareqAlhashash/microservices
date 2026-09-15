package com.investorbook.invoiceservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;

import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.invoiceservice.dao.InvoiceRepository;
import com.investorbook.invoiceservice.dao.ProcessedEventRepository;
import com.investorbook.invoiceservice.dao.entities.Invoice;
import com.investorbook.invoiceservice.dao.entities.ProcessedEvent;

@ExtendWith(MockitoExtension.class)
class InvoiceEventListenerTest {

	@Mock
	private ProcessedEventRepository processedEventRepository;

	@Mock
	private InvoiceRepository invoiceRepository;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	private InvoiceEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new InvoiceEventListener(processedEventRepository, invoiceRepository, kafkaTemplate);
	}

	private static PaymentSucceeded paymentSucceeded() {
		return new PaymentSucceeded("evt-1", "order-1", "jane@example.com", new BigDecimal("50.00"), Instant.now());
	}

	@Test
	void onPaymentSucceeded_savesAnInvoice_andPublishesInvoiceIssued() {
		listener.onPaymentSucceeded(paymentSucceeded());

		ArgumentCaptor<Invoice> savedInvoice = ArgumentCaptor.forClass(Invoice.class);
		verify(invoiceRepository).save(savedInvoice.capture());
		assertThat(savedInvoice.getValue().getOrderId()).isEqualTo("order-1");
		assertThat(savedInvoice.getValue().getInvoiceNumber()).startsWith("INV-");

		ArgumentCaptor<InvoiceIssued> published = ArgumentCaptor.forClass(InvoiceIssued.class);
		verify(kafkaTemplate).send(eq(Topics.INVOICE_ISSUED), eq("order-1"), published.capture());
		assertThat(published.getValue().getInvoiceNumber()).isEqualTo(savedInvoice.getValue().getInvoiceNumber());
	}

	@Test
	void aRedeliveredEvent_isIgnored_savesNoInvoiceAndPublishesNothing() {
		when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		listener.onPaymentSucceeded(paymentSucceeded());

		verify(invoiceRepository, never()).save(any());
		verify(kafkaTemplate, never()).send(any(String.class), any(), any());
	}
}
