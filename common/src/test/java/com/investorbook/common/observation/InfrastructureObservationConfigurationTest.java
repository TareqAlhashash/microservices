package com.investorbook.common.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

class InfrastructureObservationConfigurationTest {

	private final ObservationPredicate predicate = new InfrastructureObservationConfiguration()
			.ignoreInfrastructureTraffic();

	@ParameterizedTest
	@CsvSource({ "/orders, true", "/orders/123/events, true", "/actuator/health, false",
			"/actuator/prometheus, false", "/uaa/actuator/prometheus, false" })
	void serverRequests_areObservedUnlessTheyTargetActuator(String path, boolean observed) {
		Observation.Context context = new ServerRequestObservationContext(new MockHttpServletRequest("GET", path),
				new MockHttpServletResponse());

		assertThat(predicate.test("http.server.requests", context)).isEqualTo(observed);
	}

	@ParameterizedTest
	@CsvSource({ "http://localhost:8761/eureka/apps/delta, false", "http://localhost:8761/eureka/apps/, false",
			"http://localhost:9100/uaa/login, true" })
	void clientRequests_areObservedUnlessTheyTargetEureka(String url, boolean observed) {
		Observation.Context context = new ClientRequestObservationContext(
				new MockClientHttpRequest(HttpMethod.GET, URI.create(url)));

		assertThat(predicate.test("http.client.requests", context)).isEqualTo(observed);
	}

	@Test
	void otherObservations_areObserved() {
		assertThat(predicate.test("spring.kafka.template", new Observation.Context())).isTrue();
	}
}
