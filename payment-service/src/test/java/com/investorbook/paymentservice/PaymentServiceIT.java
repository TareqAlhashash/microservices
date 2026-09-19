package com.investorbook.paymentservice;

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

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
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

import com.investorbook.common.event.InvoiceFailed;
import com.investorbook.common.event.InvoiceVoided;
import com.investorbook.common.event.OrderPlaced;
import com.investorbook.common.event.Topics;

/**
 * Proves payment-service's reaction to OrderPlaced against a real Postgres
 * (the processed_events dedupe table) and a real Kafka (Testcontainers):
 * the deterministic mock decision publishes the right terminal event, and a
 * redelivered event id does not result in a second publish.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Testcontainers
class PaymentServiceIT {

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

	// See order-service's CLAUDE.md note: .withKraft() is required, or every
	// client disconnects simultaneously about 10 seconds after this starts.
	@Container
	private static final KafkaContainer KAFKA = new KafkaContainer(
			DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withKraft();

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
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

	private void publishOrderPlaced(String eventId, String orderId, BigDecimal amount) {
		kafkaTemplate.send(Topics.ORDER_PLACED, orderId,
				new OrderPlaced(eventId, orderId, "jane@example.com", amount, Instant.now()));
	}

	@Test
	void anAmountBelowTheThreshold_resultsInPaymentSucceeded() {
		String orderId = UUID.randomUUID().toString();
		publishOrderPlaced(UUID.randomUUID().toString(), orderId, new BigDecimal("50.00"));

		try (Consumer<String, String> consumer = newConsumer("test-payment-succeeded-1", Topics.PAYMENT_SUCCEEDED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.PAYMENT_SUCCEEDED, orderId, Duration.ofSeconds(2))
							.isEmpty());
		}
	}

	@Test
	void anAmountAtTheThreshold_resultsInPaymentFailed() {
		String orderId = UUID.randomUUID().toString();
		// matches PaymentEventListener.DECLINE_THRESHOLD (package-private; not worth
		// widening its visibility just for this test to reference it directly)
		publishOrderPlaced(UUID.randomUUID().toString(), orderId, new BigDecimal("1000.00"));

		try (Consumer<String, String> consumer = newConsumer("test-payment-failed-1", Topics.PAYMENT_FAILED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.PAYMENT_FAILED, orderId, Duration.ofSeconds(2))
							.isEmpty());
		}
	}

	/**
	 * Kafka is at-least-once: a redelivered OrderPlaced (same event id) must
	 * result in exactly one PaymentSucceeded, not two - proving the
	 * processed_events dedupe table actually prevents double-processing,
	 * not just that a single delivery works.
	 */
	@Test
	void aRedeliveredOrderPlaced_resultsInOnlyOnePaymentSucceeded() {
		String orderId = UUID.randomUUID().toString();
		String eventId = UUID.randomUUID().toString();
		BigDecimal amount = new BigDecimal("50.00");

		publishOrderPlaced(eventId, orderId, amount);
		publishOrderPlaced(eventId, orderId, amount);

		try (Consumer<String, String> consumer = newConsumer("test-payment-succeeded-2", Topics.PAYMENT_SUCCEEDED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.PAYMENT_SUCCEEDED, orderId, Duration.ofSeconds(2))
							.isEmpty());
			// give the (already-processed) redelivery time to reach the listener too
			List<ConsumerRecord<String, String>> afterSettling = recordsForOrder(consumer, Topics.PAYMENT_SUCCEEDED,
					orderId, Duration.ofSeconds(5));
			assertThat(afterSettling).isEmpty();
		}
	}

	@Test
	void aFailedInvoice_resultsInPaymentRefunded() {
		String orderId = UUID.randomUUID().toString();
		kafkaTemplate.send(Topics.INVOICE_FAILED, orderId, new InvoiceFailed(UUID.randomUUID().toString(), orderId,
				"jane@example.com", new BigDecimal("600.00"), "amount exceeds the invoicing limit", Instant.now()));

		try (Consumer<String, String> consumer = newConsumer("test-payment-refunded-1", Topics.PAYMENT_REFUNDED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.PAYMENT_REFUNDED, orderId, Duration.ofSeconds(2))
							.isEmpty());
		}
	}

	@Test
	void aVoidedInvoice_resultsInPaymentRefunded() {
		String orderId = UUID.randomUUID().toString();
		kafkaTemplate.send(Topics.INVOICE_VOIDED, orderId, new InvoiceVoided(UUID.randomUUID().toString(), orderId,
				"jane@example.com", new BigDecimal("80.00"), "INV-AAAA1111", "customer could not be notified",
				Instant.now()));

		try (Consumer<String, String> consumer = newConsumer("test-payment-refunded-2", Topics.PAYMENT_REFUNDED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.PAYMENT_REFUNDED, orderId, Duration.ofSeconds(2))
							.isEmpty());
		}
	}

	/**
	 * A refund must never be issued twice for the same trigger: a redelivered
	 * InvoiceFailed (same event id) results in exactly one PaymentRefunded.
	 */
	@Test
	void aRedeliveredInvoiceFailed_resultsInOnlyOnePaymentRefunded() {
		String orderId = UUID.randomUUID().toString();
		InvoiceFailed event = new InvoiceFailed(UUID.randomUUID().toString(), orderId, "jane@example.com",
				new BigDecimal("600.00"), "amount exceeds the invoicing limit", Instant.now());
		kafkaTemplate.send(Topics.INVOICE_FAILED, orderId, event);
		kafkaTemplate.send(Topics.INVOICE_FAILED, orderId, event);

		try (Consumer<String, String> consumer = newConsumer("test-payment-refunded-3", Topics.PAYMENT_REFUNDED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.PAYMENT_REFUNDED, orderId, Duration.ofSeconds(2))
							.isEmpty());
			List<ConsumerRecord<String, String>> afterSettling = recordsForOrder(consumer, Topics.PAYMENT_REFUNDED,
					orderId, Duration.ofSeconds(5));
			assertThat(afterSettling).isEmpty();
		}
	}
}
