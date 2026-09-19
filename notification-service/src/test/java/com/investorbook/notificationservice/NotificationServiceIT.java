package com.investorbook.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.mail.internet.MimeMessage;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.Topics;

/**
 * Proves notification-service's reaction to InvoiceIssued against a real
 * Postgres (the processed_events dedupe table), a real Kafka
 * (Testcontainers), and a real SMTP send (GreenMail, a fake mail server -
 * see NotificationEmailSender for why this is real sending code, not just
 * a log statement): a real email is sent and OrderCompleted is published,
 * and a redelivered event id results in neither happening twice.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Testcontainers
class NotificationServiceIT {

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

	// See order-service's CLAUDE.md note: .withKraft() is required, or every
	// client disconnects simultaneously about 10 seconds after this starts.
	@Container
	private static final KafkaContainer KAFKA = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

	@RegisterExtension
	static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("spring.mail.port", () -> ServerSetupTest.SMTP.getPort());
	}

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	private Consumer<String, String> newConsumer(String groupId, String topic) {
		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(), groupId,
				"true");
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
		consumer.subscribe(Collections.singletonList(topic));
		return consumer;
	}

	private static List<ConsumerRecord<String, String>> recordsForOrder(Consumer<String, String> consumer,
			String topic, String orderId, Duration timeout) {
		ConsumerRecords<String, String> polled = KafkaTestUtils.getRecords(consumer, timeout.toMillis());
		List<ConsumerRecord<String, String>> matching = new ArrayList<>();
		for (ConsumerRecord<String, String> record : polled.records(topic)) {
			if (record.key().equals(orderId)) {
				matching.add(record);
			}
		}
		return matching;
	}

	private void publishInvoiceIssued(String eventId, String orderId, String customerEmail, String invoiceNumber) {
		kafkaTemplate.send(Topics.INVOICE_ISSUED, orderId, new InvoiceIssued(eventId, orderId, customerEmail,
				new BigDecimal("50.00"), invoiceNumber, Instant.now()));
	}

	@Test
	void anInvoiceIssued_sendsARealEmail_andPublishesOrderCompleted() throws Exception {
		String orderId = UUID.randomUUID().toString();
		String email = "jane@example.com";
		publishInvoiceIssued(UUID.randomUUID().toString(), orderId, email, "INV-AAAA1111");

		await().atMost(Duration.ofSeconds(15)).until(() -> greenMail.getReceivedMessages().length >= 1);
		MimeMessage received = greenMail.getReceivedMessages()[0];
		assertThat(received.getAllRecipients()[0].toString()).isEqualTo(email);
		assertThat(received.getSubject()).contains("INV-AAAA1111");

		try (Consumer<String, String> consumer = newConsumer("test-order-completed-1", Topics.ORDER_COMPLETED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.ORDER_COMPLETED, orderId, Duration.ofSeconds(2))
							.isEmpty());
		}
	}

	/**
	 * Kafka is at-least-once: a redelivered InvoiceIssued (same event id) must
	 * result in exactly one email and one OrderCompleted, not two.
	 */
	@Test
	void aRedeliveredInvoiceIssued_sendsOnlyOneEmailAndPublishesOrderCompletedOnlyOnce() throws Exception {
		String orderId = UUID.randomUUID().toString();
		String email = "duplicate@example.com";
		String eventId = UUID.randomUUID().toString();

		publishInvoiceIssued(eventId, orderId, email, "INV-BBBB2222");
		publishInvoiceIssued(eventId, orderId, email, "INV-BBBB2222");

		try (Consumer<String, String> consumer = newConsumer("test-order-completed-2", Topics.ORDER_COMPLETED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.ORDER_COMPLETED, orderId, Duration.ofSeconds(2))
							.isEmpty());
			// give the (already-processed) redelivery time to reach the listener too
			List<ConsumerRecord<String, String>> afterSettling = recordsForOrder(consumer, Topics.ORDER_COMPLETED,
					orderId, Duration.ofSeconds(5));
			assertThat(afterSettling).isEmpty();
		}

		long emailsToRecipient = 0;
		for (MimeMessage message : greenMail.getReceivedMessages()) {
			if (message.getAllRecipients()[0].toString().equals(email)) {
				emailsToRecipient++;
			}
		}
		assertThat(emailsToRecipient).isEqualTo(1);
	}

	/**
	 * The notification step's real failure, end to end against a real Kafka and
	 * a real SMTP server: an address that can never receive mail publishes
	 * NotificationFailed (which starts the void-invoice and refund chain), no
	 * mail is sent, and OrderCompleted is NOT published.
	 */
	@Test
	void anUndeliverableRecipient_publishesNotificationFailed_andNeverOrderCompleted() throws Exception {
		String orderId = UUID.randomUUID().toString();
		publishInvoiceIssued(UUID.randomUUID().toString(), orderId, "not-an-email-address", "INV-CCCC3333");

		try (Consumer<String, String> failed = newConsumer("test-notification-failed-1", Topics.NOTIFICATION_FAILED);
				Consumer<String, String> completed = newConsumer("test-order-completed-3", Topics.ORDER_COMPLETED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(failed, Topics.NOTIFICATION_FAILED, orderId, Duration.ofSeconds(2))
							.isEmpty());
			assertThat(recordsForOrder(completed, Topics.ORDER_COMPLETED, orderId, Duration.ofSeconds(3))).isEmpty();
		}
		// other tests in this class do send mail, so look only for this order's invoice
		for (MimeMessage message : greenMail.getReceivedMessages()) {
			assertThat(message.getSubject()).doesNotContain("INV-CCCC3333");
		}
	}
}
