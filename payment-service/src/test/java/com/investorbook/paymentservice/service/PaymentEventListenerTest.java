package com.investorbook.paymentservice.service;

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

import com.investorbook.common.event.InvoiceFailed;
import com.investorbook.common.event.InvoiceVoided;
import com.investorbook.common.event.OrderPlaced;
import com.investorbook.common.event.PaymentFailed;
import com.investorbook.common.event.PaymentRefunded;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.paymentservice.dao.ProcessedEventRepository;
import com.investorbook.paymentservice.dao.entities.ProcessedEvent;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

	@Mock
	private ProcessedEventRepository processedEventRepository;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	private PaymentEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new PaymentEventListener(processedEventRepository, kafkaTemplate);
	}

	private static OrderPlaced orderPlaced(String amount) {
		return new OrderPlaced("evt-1", "order-1", "jane@example.com", new BigDecimal(amount), Instant.now());
	}

	@Test
	void anAmountBelowTheThreshold_publishesPaymentSucceeded_keyedByOrderId() {
		listener.onOrderPlaced(orderPlaced("50.00"));

		ArgumentCaptor<PaymentSucceeded> event = ArgumentCaptor.forClass(PaymentSucceeded.class);
		verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq(Topics.PAYMENT_SUCCEEDED),
				org.mockito.ArgumentMatchers.eq("order-1"), event.capture());
		assertThat(event.getValue().getOrderId()).isEqualTo("order-1");
		assertThat(event.getValue().getAmount()).isEqualByComparingTo("50.00");
	}

	@Test
	void anAmountAtOrAboveTheThreshold_publishesPaymentFailed() {
		listener.onOrderPlaced(orderPlaced("1000.00"));

		ArgumentCaptor<PaymentFailed> event = ArgumentCaptor.forClass(PaymentFailed.class);
		verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq(Topics.PAYMENT_FAILED),
				org.mockito.ArgumentMatchers.eq("order-1"), event.capture());
		assertThat(event.getValue().getReason()).contains("threshold");
	}

	@Test
	void aRedeliveredEvent_isIgnored_publishesNothing() {
		when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		listener.onOrderPlaced(orderPlaced("50.00"));

		verify(kafkaTemplate, never()).send(any(String.class), any(), any());
	}

	private static InvoiceFailed invoiceFailed() {
		return new InvoiceFailed("evt-2", "order-1", "jane@example.com", new BigDecimal("600.00"),
				"amount exceeds the invoicing limit", Instant.now());
	}

	private static InvoiceVoided invoiceVoided() {
		return new InvoiceVoided("evt-3", "order-1", "jane@example.com", new BigDecimal("80.00"), "INV-ABCD1234",
				"customer could not be notified", Instant.now());
	}

	@Test
	void aFailedInvoice_refundsThePayment_publishingPaymentRefundedKeyedByOrderId() {
		listener.onInvoiceFailed(invoiceFailed());

		ArgumentCaptor<PaymentRefunded> event = ArgumentCaptor.forClass(PaymentRefunded.class);
		verify(kafkaTemplate).send(eq(Topics.PAYMENT_REFUNDED), eq("order-1"), event.capture());
		assertThat(event.getValue().getOrderId()).isEqualTo("order-1");
		assertThat(event.getValue().getCustomerEmail()).isEqualTo("jane@example.com");
		assertThat(event.getValue().getAmount()).isEqualByComparingTo("600.00");
		assertThat(event.getValue().getReason()).contains("invoice could not be issued")
				.contains("amount exceeds the invoicing limit");
	}

	@Test
	void aVoidedInvoice_refundsThePayment_publishingPaymentRefundedKeyedByOrderId() {
		listener.onInvoiceVoided(invoiceVoided());

		ArgumentCaptor<PaymentRefunded> event = ArgumentCaptor.forClass(PaymentRefunded.class);
		verify(kafkaTemplate).send(eq(Topics.PAYMENT_REFUNDED), eq("order-1"), event.capture());
		assertThat(event.getValue().getAmount()).isEqualByComparingTo("80.00");
		assertThat(event.getValue().getReason()).contains("INV-ABCD1234").contains("customer could not be notified");
	}

	@Test
	void aRedeliveredInvoiceFailed_isIgnored_refundsNothingTwice() {
		when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		listener.onInvoiceFailed(invoiceFailed());

		verify(kafkaTemplate, never()).send(any(String.class), any(), any());
	}

	@Test
	void aRedeliveredInvoiceVoided_isIgnored_refundsNothingTwice() {
		when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		listener.onInvoiceVoided(invoiceVoided());

		verify(kafkaTemplate, never()).send(any(String.class), any(), any());
	}
}
