package com.investorbook.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

class InfrastructureObservationConfigTest {

	private final ObservationPredicate predicate = new InfrastructureObservationConfig()
			.ignoreInfrastructureTraffic();

	@ParameterizedTest
	@CsvSource({ "/order-service/orders, true", "/login, true", "/actuator/health, false",
			"/actuator/prometheus, false" })
	void serverRequests_areObservedUnlessTheyTargetActuator(String path, boolean observed) {
		Observation.Context context = new ServerRequestObservationContext(
				MockServerHttpRequest.get(path).build(), new MockServerHttpResponse(), new HashMap<>());

		assertThat(predicate.test("http.server.requests", context)).isEqualTo(observed);
	}

	@ParameterizedTest
	@CsvSource({ "http://localhost:8761/eureka/apps/delta, false", "http://localhost:8761/eureka/apps/, false",
			"http://localhost:8200/orders, true" })
	void clientRequests_areObservedUnlessTheyTargetEureka(String url, boolean observed) {
		Observation.Context context = new ClientRequestObservationContext(
				new MockClientHttpRequest(HttpMethod.GET, URI.create(url)));

		assertThat(predicate.test("http.client.requests", context)).isEqualTo(observed);
	}

	@Test
	void otherObservations_areObserved() {
		assertThat(predicate.test("spring.security.filterchains", new Observation.Context())).isTrue();
	}
}
