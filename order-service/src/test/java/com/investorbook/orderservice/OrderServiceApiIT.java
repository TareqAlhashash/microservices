package com.investorbook.orderservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
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
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
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
import com.investorbook.common.event.PaymentRefunded;
import com.investorbook.common.event.PaymentSucceeded;
import com.investorbook.common.event.Topics;
import com.investorbook.orderservice.dto.OrderResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

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
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@AutoConfigureTestRestTemplate
@Testcontainers
class OrderServiceApiIT {

	// NimbusJwtDecoder.withPublicKey only verifies asymmetric (RSA) signatures - unlike the old
	// resource server this replaced, which could be pointed at a plain HMAC secret for a quick
	// test override (see api-gateway's ApiGatewaySecurityIT for the same reasoning).
	private static final KeyPair KEY_PAIR = generateRsaKeyPair();

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
		registry.add("investorbook.security.jwt.public.key", OrderServiceApiIT::publicKeyPem);
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private KafkaTemplate<String, Object> kafkaTemplate;

	private static KeyPair generateRsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String publicKeyPem() {
		String base64 = Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
		return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
	}

	private static String tokenFor(String email) {
		try {
			JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(email).claim("user_name", email)
					.claim("authorities", List.of("ROLE_MEMBER")).issueTime(Date.from(Instant.now()))
					.expirationTime(Date.from(Instant.now().plusSeconds(3600))).build();
			SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
			signedJwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
			return signedJwt.serialize();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
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
				"order-placed-probe", true);
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

		// Other test methods in this class also call placeOrder(), so more than one
		// OrderPlaced record can legitimately exist on this topic by the time this
		// runs - find the one for THIS test's order rather than assuming there's
		// only ever one.
		try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
			consumer.subscribe(Collections.singletonList(Topics.ORDER_PLACED));

			// KafkaTestUtils.getRecords now takes a Duration directly (this version's API flipped
			// from the long-milliseconds overload documented in CLAUDE.md for the previous one).
			ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));
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
	 * Compensation chain A: invoice-service could not issue an invoice, so
	 * payment-service refunded the payment. The order is still PAID when the
	 * refund arrives and must end up CANCELLED, not stuck PAID forever.
	 */
	@Test
	void aRefundAfterAFailedInvoice_cancelsAPaidOrder() {
		String email = "invoice-failed@example.com";
		BigDecimal amount = new BigDecimal("600.00");
		String orderId = placeOrder(email, amount.toString());

		kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, orderId,
				new PaymentSucceeded(UUID.randomUUID().toString(), orderId, email, amount, Instant.now()));
		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("PAID"));

		kafkaTemplate.send(Topics.PAYMENT_REFUNDED, orderId, new PaymentRefunded(UUID.randomUUID().toString(),
				orderId, email, amount, "invoice could not be issued", Instant.now()));

		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("CANCELLED"));
	}

	/**
	 * Compensation chain B: the customer could not be notified, so the invoice
	 * was voided and the payment refunded. The order had already reached
	 * INVOICED and must end up CANCELLED, not stuck INVOICED forever.
	 */
	@Test
	void aRefundAfterAFailedNotification_cancelsAnInvoicedOrder() {
		String email = "notification-failed@example.com";
		BigDecimal amount = new BigDecimal("80.00");
		String orderId = placeOrder(email, amount.toString());

		kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, orderId,
				new PaymentSucceeded(UUID.randomUUID().toString(), orderId, email, amount, Instant.now()));
		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("PAID"));
		kafkaTemplate.send(Topics.INVOICE_ISSUED, orderId, new InvoiceIssued(UUID.randomUUID().toString(), orderId,
				email, amount, "INV-0002", Instant.now()));
		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("INVOICED"));

		kafkaTemplate.send(Topics.PAYMENT_REFUNDED, orderId, new PaymentRefunded(UUID.randomUUID().toString(),
				orderId, email, amount, "customer could not be notified", Instant.now()));

		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("CANCELLED"));
	}

	/**
	 * A redelivered PaymentRefunded must leave the order CANCELLED (not error,
	 * and not move it anywhere else), same state-machine-guard idempotency as
	 * every other transition here.
	 */
	@Test
	void aRedeliveredPaymentRefunded_leavesTheOrderCancelled() {
		String email = "duplicate-refund@example.com";
		BigDecimal amount = new BigDecimal("90.00");
		String orderId = placeOrder(email, amount.toString());
		kafkaTemplate.send(Topics.PAYMENT_SUCCEEDED, orderId,
				new PaymentSucceeded(UUID.randomUUID().toString(), orderId, email, amount, Instant.now()));
		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("PAID"));

		PaymentRefunded event = new PaymentRefunded(UUID.randomUUID().toString(), orderId, email, amount,
				"invoice could not be issued", Instant.now());
		kafkaTemplate.send(Topics.PAYMENT_REFUNDED, orderId, event);
		kafkaTemplate.send(Topics.PAYMENT_REFUNDED, orderId, event);

		await().atMost(Duration.ofSeconds(10)).until(() -> statusOf(orderId, email).equals("CANCELLED"));
		await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
				.until(() -> statusOf(orderId, email).equals("CANCELLED"));
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

	/**
	 * spring.data.web.pageable.max-page-size=100 (application.properties) is what's actually
	 * meant to enforce this, not application code (see OrderController.listOrders) - this
	 * proves the property really takes effect over real HTTP dispatch, not just that it's set.
	 */
	private static final ParameterizedTypeReference<PagedModel<OrderResponse>> ORDERS_PAGE_TYPE = new ParameterizedTypeReference<PagedModel<OrderResponse>>() {
	};

	@Test
	void listOrders_capsAnOversizedRequestedPageSize() {
		String email = "paging@example.com";
		placeOrder(email, "10.00");

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenFor(email));
		ResponseEntity<PagedModel<OrderResponse>> response = restTemplate.exchange("/orders?size=99999",
				HttpMethod.GET, new HttpEntity<>(headers), ORDERS_PAGE_TYPE);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getMetadata().getSize()).isEqualTo(100);
	}

	@Test
	void listOrders_filtersServerSide_byOrderId() {
		String email = "search@example.com";
		String orderId = placeOrder(email, "25.00");

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenFor(email));
		ResponseEntity<PagedModel<OrderResponse>> response = restTemplate.exchange(
				"/orders?search=" + orderId.substring(0, 8), HttpMethod.GET, new HttpEntity<>(headers),
				ORDERS_PAGE_TYPE);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getContent()).extracting(OrderResponse::getId).contains(orderId);
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
