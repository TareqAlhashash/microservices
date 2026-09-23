package com.investorbook.apigateway.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.investorbook.apigateway.dto.ActuatorHealthResponse;
import com.investorbook.apigateway.dto.ServiceStatusResponse;

/**
 * Lenient strictness: every test only cares about one or two of the nine services this
 * controller checks on every call, and @BeforeEach stubs a blanket "everything is down"
 * default for the rest so each test doesn't have to stub all nine to avoid a strict-stubbing
 * argument-mismatch failure on the services it doesn't care about.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardControllerTest {

	@Mock
	private DiscoveryClient discoveryClient;

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private ServiceInstance orderServiceInstance;

	private DashboardController controller;

	@BeforeEach
	void setUp() {
		when(discoveryClient.getInstances(anyString())).thenReturn(Collections.emptyList());
		doReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()).when(restTemplate)
				.getForEntity(anyString(), any());
		controller = new DashboardController(discoveryClient, restTemplate);
	}

	private static ResponseEntity<ActuatorHealthResponse> up() {
		ActuatorHealthResponse body = new ActuatorHealthResponse();
		body.setStatus("UP");
		return ResponseEntity.ok(body);
	}

	@Test
	void serviceStatuses_reportsUp_whenTheServiceIsRegisteredAndHealthy() {
		when(discoveryClient.getInstances("order-service"))
				.thenReturn(Collections.singletonList(orderServiceInstance));
		when(orderServiceInstance.getUri()).thenReturn(URI.create("http://localhost:8200"));
		doReturn(up()).when(restTemplate).getForEntity(eq("http://localhost:8200/actuator/health"), any());

		List<ServiceStatusResponse> statuses = controller.serviceStatuses();

		ServiceStatusResponse orderService = findByName(statuses, "order-service");
		assertThat(orderService.getStatus()).isEqualTo("UP");
		assertThat(orderService.getUrl()).isEqualTo("http://localhost:8200");
	}

	@Test
	void serviceStatuses_reportsDown_whenNothingIsRegisteredWithEureka() {
		List<ServiceStatusResponse> statuses = controller.serviceStatuses();

		assertThat(findByName(statuses, "order-service").getStatus()).isEqualTo("DOWN");
	}

	@Test
	void serviceStatuses_reportsDown_whenTheHealthCallFails() {
		when(discoveryClient.getInstances("order-service"))
				.thenReturn(Collections.singletonList(orderServiceInstance));
		when(orderServiceInstance.getUri()).thenReturn(URI.create("http://localhost:8200"));
		doThrow(new ResourceAccessException("connection refused")).when(restTemplate)
				.getForEntity(eq("http://localhost:8200/actuator/health"), any());

		List<ServiceStatusResponse> statuses = controller.serviceStatuses();

		assertThat(findByName(statuses, "order-service").getStatus()).isEqualTo("DOWN");
	}

	@Test
	void serviceStatuses_checksEurekaServerDirectly_notThroughDiscovery() {
		doReturn(up()).when(restTemplate).getForEntity(eq("http://localhost:8761/actuator/health"), any());

		List<ServiceStatusResponse> statuses = controller.serviceStatuses();

		ServiceStatusResponse eureka = findByName(statuses, "eureka-server");
		assertThat(eureka.getStatus()).isEqualTo("UP");
		assertThat(eureka.getUrl()).isEqualTo("http://localhost:8761");
	}

	@Test
	void serviceStatuses_coversEveryKnownService() {
		List<ServiceStatusResponse> statuses = controller.serviceStatuses();

		assertThat(statuses).extracting(ServiceStatusResponse::getName).containsExactlyInAnyOrder("eureka-server",
				"api-gateway", "auth-service", "member-service", "resource-service", "order-service",
				"payment-service", "invoice-service", "notification-service");
	}

	private static ServiceStatusResponse findByName(List<ServiceStatusResponse> statuses, String name) {
		return statuses.stream().filter(s -> s.getName().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError(name + " missing from " + statuses));
	}
}
