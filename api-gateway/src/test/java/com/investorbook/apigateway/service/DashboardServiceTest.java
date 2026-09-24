package com.investorbook.apigateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.investorbook.apigateway.dto.ServiceStatusResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Stubs WebClient at the ExchangeFunction seam (a single functional interface - request in,
 * Mono<ClientResponse> out) rather than mocking WebClient's own fluent builder chain method by
 * method, which is the standard, documented way to unit-test WebClient-calling code.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

	@Mock
	private ReactiveDiscoveryClient discoveryClient;

	@Mock
	private ServiceInstance orderServiceInstance;

	@BeforeEach
	void setUp() {
		lenient().when(discoveryClient.getInstances(anyString())).thenReturn(Flux.empty());
	}

	private DashboardService serviceRespondingWith(Map<String, HttpStatus> healthByUrl) {
		WebClient webClient = WebClient.builder().exchangeFunction(request -> {
			HttpStatus status = healthByUrl.get(request.url().toString());
			if (status == null) {
				return Mono.error(new RuntimeException("connection refused"));
			}
			ClientResponse response = ClientResponse.create(status)
					.header("Content-Type", "application/json")
					.body(status == HttpStatus.OK ? "{\"status\":\"UP\"}" : "{}").build();
			return Mono.just(response);
		}).build();
		return new DashboardService(discoveryClient, webClient);
	}

	@Test
	void serviceStatuses_reportsUp_whenTheServiceIsRegisteredAndHealthy() {
		when(discoveryClient.getInstances("order-service")).thenReturn(Flux.just(orderServiceInstance));
		when(orderServiceInstance.getUri()).thenReturn(URI.create("http://localhost:8200"));
		DashboardService dashboardService = serviceRespondingWith(
				Map.of("http://localhost:8200/actuator/health", HttpStatus.OK));

		List<ServiceStatusResponse> statuses = dashboardService.serviceStatuses().block();

		ServiceStatusResponse orderService = findByName(statuses, "order-service");
		assertThat(orderService.getStatus()).isEqualTo("UP");
		assertThat(orderService.getUrl()).isEqualTo("http://localhost:8200");
	}

	@Test
	void serviceStatuses_reportsDown_whenNothingIsRegisteredWithEureka() {
		DashboardService dashboardService = serviceRespondingWith(Map.of());

		List<ServiceStatusResponse> statuses = dashboardService.serviceStatuses().block();

		assertThat(findByName(statuses, "order-service").getStatus()).isEqualTo("DOWN");
	}

	@Test
	void serviceStatuses_reportsDown_whenTheHealthCallFails() {
		when(discoveryClient.getInstances("order-service")).thenReturn(Flux.just(orderServiceInstance));
		when(orderServiceInstance.getUri()).thenReturn(URI.create("http://localhost:8200"));
		// no stubbed URL for order-service's health check - the exchange function errors, same
		// as a real connection-refused would.
		DashboardService dashboardService = serviceRespondingWith(Map.of());

		List<ServiceStatusResponse> statuses = dashboardService.serviceStatuses().block();

		assertThat(findByName(statuses, "order-service").getStatus()).isEqualTo("DOWN");
	}

	@Test
	void serviceStatuses_checksEurekaServerDirectly_notThroughDiscovery() {
		DashboardService dashboardService = serviceRespondingWith(
				Map.of("http://localhost:8761/actuator/health", HttpStatus.OK));

		List<ServiceStatusResponse> statuses = dashboardService.serviceStatuses().block();

		ServiceStatusResponse eureka = findByName(statuses, "eureka-server");
		assertThat(eureka.getStatus()).isEqualTo("UP");
		assertThat(eureka.getUrl()).isEqualTo("http://localhost:8761");
	}

	@Test
	void serviceStatuses_coversEveryKnownService() {
		DashboardService dashboardService = serviceRespondingWith(Map.of());

		List<ServiceStatusResponse> statuses = dashboardService.serviceStatuses().block();

		assertThat(statuses).extracting(ServiceStatusResponse::getName).containsExactlyInAnyOrder("eureka-server",
				"api-gateway", "auth-service", "order-service", "payment-service", "invoice-service",
				"notification-service");
	}

	private static ServiceStatusResponse findByName(List<ServiceStatusResponse> statuses, String name) {
		return statuses.stream().filter(s -> s.getName().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError(name + " missing from " + statuses));
	}
}
