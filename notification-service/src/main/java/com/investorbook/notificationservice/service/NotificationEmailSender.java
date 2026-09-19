package com.investorbook.notificationservice.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.investorbook.common.event.InvoiceIssued;

/**
 * Real JavaMailSender-based sending code, exercised for real against a fake
 * SMTP server (GreenMail) in tests - per the plan, email is mocked in
 * production in the sense that no real mail server is ever configured
 * (spring.mail.host defaults to a harmless localhost placeholder), not in
 * the sense that this code merely logs and calls it done.
 */
@Component
public class NotificationEmailSender {

	private final JavaMailSender mailSender;

	public NotificationEmailSender(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	/**
	 * @throws UndeliverableRecipientException if the customer's address can never
	 *         receive mail; any other exception is a send failure (mail server
	 *         unreachable and the like) that a retry might get past.
	 */
	public void sendCompletionEmail(InvoiceIssued event) throws Exception {
		String recipient = event.getCustomerEmail();
		if (recipient == null || !recipient.contains("@")) {
			throw new UndeliverableRecipientException("recipient address is missing or malformed");
		}

		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
		try {
			helper.setTo(recipient);
		} catch (AddressException e) {
			throw new UndeliverableRecipientException("recipient address is missing or malformed", e);
		}
		helper.setSubject("Your order is complete - invoice " + event.getInvoiceNumber());
		helper.setText("Thanks for your order (" + event.getOrderId() + "). Your payment of $" + event.getAmount()
				+ " was processed and invoice " + event.getInvoiceNumber() + " is attached.");

		String invoiceText = "Invoice " + event.getInvoiceNumber() + "\nOrder: " + event.getOrderId() + "\nAmount: $"
				+ event.getAmount() + "\nIssued: " + event.getOccurredAt();
		byte[] invoiceBytes = invoiceText.getBytes(StandardCharsets.UTF_8);
		helper.addAttachment(event.getInvoiceNumber() + ".txt",
				() -> new ByteArrayInputStream(invoiceBytes));

		mailSender.send(message);
	}
}
