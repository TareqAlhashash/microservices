package com.investorbook.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.investorbook.common.event.InvoiceIssued;
import com.investorbook.common.event.OrderCompleted;
import com.investorbook.common.event.PaymentFailed;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.orderservice.service.OrderResponse;

/**
 * Proves order-service's half of the purchase saga against a real Postgres
 * and a real Kafka (Testcontainers): placing an order really publishes
 * OrderPlaced, and the order's own status really advances as the saga's
 * terminal events arrive - including the compensating path (PaymentFailed)
 * and idempotency (a redelivered event doesn't double-process).
 *
 * payment-service/invoice-service/notification-service aren't running here
 * - each proves its own reaction to the event before it in its own IT suite
 * - so this test publishes their events itself, standing in for them. That's
 * a deliberate choice: a single test spinning up four separate deployables
 * in one JVM would be fragile and unlike how any other test in this repo
 * works; proving each link of the chain separately (this service's producer
 * and consumer sides here, each other service's reaction to its trigger
 * event in its own suite) proves the same thing without it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "eureka.client.enabled=false",
		"security.oauth2.resource.jwt.key-value=test-signing-secret-please-ignore" })
@Testcontainers
class OrderServiceApiIT {

	private static final String SIGNING_KEY = "test-signing-secret-please-ignore";

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

	// Testcontainers' KafkaContainer defaults to embedded-Zookeeper mode unless told
	// otherwise; withKraft() matches how the local docker-compose Kafka runs (see
	// docker-compose.yml) and avoids a flaky embedded-Zookeeper start with this image.
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
	private TestRestTemplate restTemplate;

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	private static String tokenFor(String email) {
		JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
		converter.setSigningKey(SIGNING_KEY);

		OAuth2Request request = new OAuth2Request(Collections.emptyMap(), "html5", Collections.emptyList(), true,
				Collections.singleton("read"), Collections.emptySet(), null, Collections.emptySet(),
				Collections.emptyMap());
		UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(email, null,
				AuthorityUtils.createAuthorityList("ROLE_MEMBER"));
		OAuth2Authentication authentication = new OAuth2Authentication(request, userAuth);

		OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken("placeholder");
		return converter.enhance(accessToken, authentication).getValue();
	}

	private HttpEntity<Map<String, Object>> placeOrderRequest(String email, String amount) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenFor(email));
		return new HttpEntity<>(Collections.singletonMap("amount", amount), headers);
	}

	private String placeOrder(String email, String amount) {
		ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/orders",
				placeOrderRequest(email, amount), OrderResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody().getId();
	}

	private String statusOf(String orderId, String email) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenFor(email));
		ResponseEntity<OrderResponse> response = restTemplate.exchange("/orders/" + orderId, HttpMethod.GET,
				new HttpEntity<>(headers), OrderResponse.class);
		return response.getBody().getStatus();
	}

	@Test
	void placingAnOrder_publishesOrderPlaced_withTheOrderIdAsTheKafkaKey() {
		// Placed first, deliberately: order.placed may not exist as a topic at all
		// until this send auto-creates it, and subscribing to a not-yet-existing
		// topic is an unreliable way to test against a just-in-time-created one.
		// Subscribing afterwards with auto.offset.reset=earliest still sees it.
		String orderId = placeOrder("probe@example.com", "42.50");

		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(KAFKA.getBootstrapServers(),
				"order-placed-probe", "true");
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		// Other test methods in this class also call placeOrder(), so more than one
		// OrderPlaced record can legitimately exist on this topic by the time this
		// runs - find the one for THIS test's order rather than assuming there's
		// only ever one.
		try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
			consumer.subscribe(Collections.singletonList(Topics.ORDER_PLACED));

			ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer,
					Duration.ofSeconds(15).toMillis());
			ConsumerRecord<String, String> matching = findRecordByKey(records, Topics.ORDER_PLACED, orderId);
			assertThat(matching.value()).contains(orderId);
		}
	}

	@Test
	void theHappyPath_advancesAnOrderAllTheWayToCompleted() {
		String email = "jane@example.com";
		BigDecimal amount = new BigDecimal("50.00");
		String orderId = placeOrder(email, amount.toString());
		assertThat(statusOf(orderId, email)).isEqualTo("PLACED");

		kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, orderId,
				new PaymentSucceeded(UUID.randomUUID().toString(), orderId, email, amount, Instant.now()));
		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("PAID"));

		kafkaTemplate.send(Topics.INVOICE_ISSUED, orderId, new InvoiceIssued(UUID.randomUUID().toString(), orderId,
				email, amount, "INV-0001", Instant.now()));
		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("INVOICED"));

		kafkaTemplate.send(Topics.ORDER_COMPLETED, orderId,
				new OrderCompleted(UUID.randomUUID().toString(), orderId, email, Instant.now()));
		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("COMPLETED"));
	}

	/**
	 * The compensating action that makes this a saga, not just a happy-path
	 * event chain: a failed payment cancels the order rather than leaving it
	 * stuck PLACED forever.
	 */
	@Test
	void aFailedPayment_cancelsTheOrder() {
		String email = "declined@example.com";
		BigDecimal amount = new BigDecimal("999.00");
		String orderId = placeOrder(email, amount.toString());

		kafkaTemplate.send(Topics.PAYMENT_FAILED, orderId, new PaymentFailed(UUID.randomUUID().toString(), orderId,
				email, amount, "card declined", Instant.now()));

		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("PAYMENT_FAILED"));
	}

	/**
	 * Kafka is at-least-once: a redelivered PaymentSucceeded must not move the
	 * order past PAID a second time (or error). order-service's idempotency
	 * strategy is a state-machine guard (see OrderEventListener), not a
	 * dedupe table - this proves it holds up against a real duplicate delivery.
	 */
	@Test
	void aRedeliveredPaymentSucceeded_doesNotDoubleProcess() {
		String email = "duplicate@example.com";
		BigDecimal amount = new BigDecimal("75.00");
		String orderId = placeOrder(email, amount.toString());

		PaymentSucceeded event = new PaymentSucceeded(UUID.randomUUID().toString(), orderId, email, amount,
				Instant.now());
		kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, orderId, event);
		kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, orderId, event);

		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("PAID"));
		// give the (already-processed) redelivery time to reach the listener too
		await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
				.until(() -> statusOf(orderId, email).equals("PAID"));
	}

	private static ConsumerRecord<String, String> findRecordByKey(ConsumerRecords<String, String> records,
			String topic, String key) {
		for (ConsumerRecord<String, String> record : records.records(topic)) {
			if (record.key().equals(key)) {
				return record;
			}
		}
		throw new AssertionError("no record with key " + key + " found on topic " + topic);
	}
}
