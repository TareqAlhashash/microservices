package com.investorbook.notificationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import com.investorbook.common.event.InvoiceIssued;

@ExtendWith(MockitoExtension.class)
class NotificationEmailSenderTest {

	@Mock
	private JavaMailSender mailSender;

	private NotificationEmailSender sender;

	@BeforeEach
	void setUp() {
		sender = new NotificationEmailSender(mailSender);
		// lenient: addresses rejected up front never reach createMimeMessage
		lenient().when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
	}

	private static InvoiceIssued invoiceIssuedFor(String customerEmail) {
		return new InvoiceIssued("evt-1", "order-1", customerEmail, new BigDecimal("50.00"), "INV-ABCD1234",
				Instant.now());
	}

	@Test
	void aDeliverableAddress_sendsTheMessageToThatRecipient() throws Exception {
		sender.sendCompletionEmail(invoiceIssuedFor("jane@example.com"));

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(sent.capture());
		assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("jane@example.com");
	}

	/**
	 * A permanent, address-level failure is different in kind from an SMTP
	 * outage: retrying can never help, so it must surface as its own exception
	 * (which the listener turns into the NotificationFailed compensation)
	 * rather than as a generic send failure the listener treats as best-effort.
	 */
	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", "no-at-sign", "one@example.com, two@example.com" })
	void anUndeliverableAddress_throwsUndeliverableRecipientException_andSendsNothing(String address) {
		assertThatThrownBy(() -> sender.sendCompletionEmail(invoiceIssuedFor(address)))
				.isInstanceOf(UndeliverableRecipientException.class);

		verify(mailSender, never()).send(any(MimeMessage.class));
	}
}
