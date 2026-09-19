package com.investorbook.invoiceservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;

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

	private static PaymentSucceeded paymentSucceeded(String amount) {
		return new PaymentSucceeded("evt-1", "order-1", "jane@example.com", new BigDecimal(amount), Instant.now());
	}

	private static NotificationFailed notificationFailed() {
		return new NotificationFailed("evt-9", "order-1", "jane@example.com", new BigDecimal("50.00"), "INV-ABCD1234",
				"recipient address is undeliverable", Instant.now());
	}

	private static Invoice issuedInvoice() {
		return new Invoice("inv-id", "order-1", "INV-ABCD1234", new BigDecimal("50.00"), Instant.now());
	}

	@Test
	void anAmountJustBelowTheInvoicingLimit_stillIssuesTheInvoice() {
		listener.onPaymentSucceeded(paymentSucceeded("499.99"));

		verify(invoiceRepository).save(any(Invoice.class));
		verify(kafkaTemplate).send(eq(Topics.INVOICE_ISSUED), eq("order-1"), any(InvoiceIssued.class));
	}

	/**
	 * The step-two failure that needs compensating: payment already succeeded, so
	 * an invoice that can't be issued must trigger InvoiceFailed (which makes
	 * payment-service refund) and must leave no invoice row and no InvoiceIssued.
	 */
	@Test
	void anAmountAtTheInvoicingLimit_publishesInvoiceFailed_andIssuesNoInvoice() {
		listener.onPaymentSucceeded(paymentSucceeded("500.00"));

		ArgumentCaptor<InvoiceFailed> published = ArgumentCaptor.forClass(InvoiceFailed.class);
		verify(kafkaTemplate).send(eq(Topics.INVOICE_FAILED), eq("order-1"), published.capture());
		assertThat(published.getValue().getOrderId()).isEqualTo("order-1");
		assertThat(published.getValue().getAmount()).isEqualByComparingTo("500.00");
		assertThat(published.getValue().getReason()).contains("limit");
		verify(invoiceRepository, never()).save(any());
		verify(kafkaTemplate, never()).send(eq(Topics.INVOICE_ISSUED), any(), any());
	}

	@Test
	void aNotificationFailure_voidsTheInvoice_andPublishesInvoiceVoided() {
		Invoice invoice = issuedInvoice();
		when(invoiceRepository.findByOrderId("order-1")).thenReturn(Optional.of(invoice));

		listener.onNotificationFailed(notificationFailed());

		assertThat(invoice.isVoided()).isTrue();
		verify(invoiceRepository).save(invoice);
		ArgumentCaptor<InvoiceVoided> published = ArgumentCaptor.forClass(InvoiceVoided.class);
		verify(kafkaTemplate).send(eq(Topics.INVOICE_VOIDED), eq("order-1"), published.capture());
		assertThat(published.getValue().getInvoiceNumber()).isEqualTo("INV-ABCD1234");
		assertThat(published.getValue().getAmount()).isEqualByComparingTo("50.00");
		assertThat(published.getValue().getReason()).contains("undeliverable");
	}

	/** Nothing to void, but the payment still has to be refunded, so the chain must continue. */
	@Test
	void aNotificationFailure_withNoInvoiceOnFile_stillPublishesInvoiceVoided() {
		when(invoiceRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

		listener.onNotificationFailed(notificationFailed());

		verify(invoiceRepository, never()).save(any());
		ArgumentCaptor<InvoiceVoided> published = ArgumentCaptor.forClass(InvoiceVoided.class);
		verify(kafkaTemplate).send(eq(Topics.INVOICE_VOIDED), eq("order-1"), published.capture());
		assertThat(published.getValue().getInvoiceNumber()).isEqualTo("INV-ABCD1234");
	}

	/** An already-voided invoice means InvoiceVoided was already published; don't refund twice. */
	@Test
	void aNotificationFailure_forAnAlreadyVoidedInvoice_publishesNothing() {
		Invoice invoice = issuedInvoice();
		invoice.markVoided(Instant.now());
		when(invoiceRepository.findByOrderId("order-1")).thenReturn(Optional.of(invoice));

		listener.onNotificationFailed(notificationFailed());

		verify(invoiceRepository, never()).save(any());
		verify(kafkaTemplate, never()).send(any(String.class), any(), any());
	}

	@Test
	void aRedeliveredNotificationFailed_isIgnored_voidsNothingAndPublishesNothing() {
		when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		listener.onNotificationFailed(notificationFailed());

		verify(invoiceRepository, never()).findByOrderId(any());
		verify(invoiceRepository, never()).save(any());
		verify(kafkaTemplate, never()).send(any(String.class), any(), any());
	}
}
