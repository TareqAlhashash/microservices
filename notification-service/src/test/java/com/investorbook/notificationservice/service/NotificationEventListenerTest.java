package com.investorbook.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.investorbook.common.event.NotificationFailed;
import com.investorbook.common.event.OrderCompleted;
import com.investorbook.common.event.Topics;
import com.investorbook.notificationservice.dao.ProcessedEventRepository;
import com.investorbook.notificationservice.dao.entities.ProcessedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

	@Mock
	private ProcessedEventRepository processedEventRepository;

	@Mock
	private NotificationEmailSender emailSender;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	private NotificationEventListener listener;

	@BeforeEach
	void setUp() {
		listener = new NotificationEventListener(processedEventRepository, emailSender, kafkaTemplate);
	}

	private static InvoiceIssued invoiceIssued() {
		return new InvoiceIssued("evt-1", "order-1", "jane@example.com", new BigDecimal("50.00"), "INV-ABCD1234",
				Instant.now());
	}

	@Test
	void onInvoiceIssued_sendsTheEmail_andPublishesOrderCompleted() throws Exception {
		listener.onInvoiceIssued(invoiceIssued());

		verify(emailSender).sendCompletionEmail(any(InvoiceIssued.class));

		ArgumentCaptor<OrderCompleted> published = ArgumentCaptor.forClass(OrderCompleted.class);
		verify(kafkaTemplate).send(eq(Topics.ORDER_COMPLETED), eq("order-1"), published.capture());
		assertThat(published.getValue().getCustomerEmail()).isEqualTo("jane@example.com");
	}

	/**
	 * The completion email is best-effort: a send failure must not stop
	 * OrderCompleted from being published, or every order would get stuck at
	 * INVOICED whenever the (never-really-configured) mail server is down.
	 */
	@Test
	void aFailedEmailSend_stillPublishesOrderCompleted() throws Exception {
		doThrow(new RuntimeException("smtp unavailable")).when(emailSender).sendCompletionEmail(any());

		listener.onInvoiceIssued(invoiceIssued());

		verify(kafkaTemplate).send(eq(Topics.ORDER_COMPLETED), eq("order-1"), any(OrderCompleted.class));
	}

	@Test
	void aRedeliveredEvent_isIgnored_sendsNoEmailAndPublishesNothing() throws Exception {
		when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		listener.onInvoiceIssued(invoiceIssued());

		verify(emailSender, never()).sendCompletionEmail(any());
		verify(kafkaTemplate, never()).send(any(String.class), any(), any());
	}

	/**
	 * A permanently undeliverable recipient is the notification step's real
	 * failure: unlike a mail-server outage, no retry can ever help, so the saga
	 * must be compensated (invoice voided, payment refunded) instead of
	 * completing, and OrderCompleted must NOT be published.
	 */
	@Test
	void anUndeliverableRecipient_publishesNotificationFailed_notOrderCompleted() throws Exception {
		doThrow(new UndeliverableRecipientException("recipient address is missing or malformed")).when(emailSender)
				.sendCompletionEmail(any());

		listener.onInvoiceIssued(invoiceIssued());

		ArgumentCaptor<NotificationFailed> published = ArgumentCaptor.forClass(NotificationFailed.class);
		verify(kafkaTemplate).send(eq(Topics.NOTIFICATION_FAILED), eq("order-1"), published.capture());
		assertThat(published.getValue().getInvoiceNumber()).isEqualTo("INV-ABCD1234");
		assertThat(published.getValue().getAmount()).isEqualByComparingTo("50.00");
		assertThat(published.getValue().getReason()).contains("malformed");
		verify(kafkaTemplate, never()).send(eq(Topics.ORDER_COMPLETED), any(), any());
	}
}
