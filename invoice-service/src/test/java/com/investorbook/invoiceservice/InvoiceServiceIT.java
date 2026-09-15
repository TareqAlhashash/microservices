package com.investorbook.invoiceservice;

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

import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.invoiceservice.dao.InvoiceRepository;

/**
 * Proves invoice-service's reaction to PaymentSucceeded against a real
 * Postgres (the invoices table and the processed_events dedupe table) and a
 * real Kafka (Testcontainers): a real Invoice row is created and
 * InvoiceIssued is published, and a redelivered event id does not result
 * in a second invoice or a second publish.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Testcontainers
class InvoiceServiceIT {

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

	@Autowired
	private InvoiceRepository invoiceRepository;

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

	private void publishPaymentSucceeded(String eventId, String orderId, BigDecimal amount) {
		kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, orderId,
				new PaymentSucceeded(eventId, orderId, "jane@example.com", amount, Instant.now()));
	}

	@Test
	void aPaymentSucceeded_resultsInAnInvoiceRowAndInvoiceIssued() {
		String orderId = UUID.randomUUID().toString();
		publishPaymentSucceeded(UUID.randomUUID().toString(), orderId, new BigDecimal("50.00"));

		try (Consumer<String, String> consumer = newConsumer("test-invoice-issued-1", Topics.INVOICE_ISSUED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.INVOICE_ISSUED, orderId, Duration.ofSeconds(2))
							.isEmpty());
		}

		await().atMost(Duration.ofSeconds(5)).until(() -> invoiceRepository.findAll().stream()
				.anyMatch(invoice -> invoice.getOrderId().equals(orderId)));
	}

	/**
	 * Kafka is at-least-once: a redelivered PaymentSucceeded (same event id)
	 * must result in exactly one InvoiceIssued and one Invoice row, not two.
	 */
	@Test
	void aRedeliveredPaymentSucceeded_resultsInOnlyOneInvoice() {
		String orderId = UUID.randomUUID().toString();
		String eventId = UUID.randomUUID().toString();
		BigDecimal amount = new BigDecimal("50.00");

		publishPaymentSucceeded(eventId, orderId, amount);
		publishPaymentSucceeded(eventId, orderId, amount);

		try (Consumer<String, String> consumer = newConsumer("test-invoice-issued-2", Topics.INVOICE_ISSUED)) {
			await().atMost(Duration.ofSeconds(15))
					.until(() -> !recordsForOrder(consumer, Topics.INVOICE_ISSUED, orderId, Duration.ofSeconds(2))
							.isEmpty());
			// give the (already-processed) redelivery time to reach the listener too
			List<ConsumerRecord<String, String>> afterSettling = recordsForOrder(consumer, Topics.INVOICE_ISSUED,
					orderId, Duration.ofSeconds(5));
			assertThat(afterSettling).isEmpty();
		}

		long invoicesForOrder = invoiceRepository.findAll().stream().filter(invoice -> invoice.getOrderId().equals(orderId))
				.count();
		assertThat(invoicesForOrder).isEqualTo(1);
	}
}
