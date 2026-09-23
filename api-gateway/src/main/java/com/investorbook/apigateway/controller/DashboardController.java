package com.investorbook.apigateway.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.investorbook.apigateway.dto.ActuatorHealthResponse;
import com.investorbook.apigateway.dto.ServiceStatusResponse;

/**
 * Aggregates health across every service in this system for the frontend's dashboard - a
 * browser can't reach any of them directly (each is a different origin/port, and only
 * api-gateway has a CORS policy, see CorsConfig), so this app calls each one server-to-server
 * instead. Deliberately unauthenticated, like every /actuator/** endpoint in this repo (see
 * order-service's SecurityConfiguration and the README's "Observability" section) - a
 * monitoring view carries the same "no bearer token" exemption a health probe does.
 */
@RestController
public class DashboardController {

	// eureka-server can't be discovered through itself (it never registers as a client -
	// eureka.client.register-with-eureka=false), so it's the one hardcoded address here;
	// every other service below is resolved live through Eureka, not a hardcoded port.
	private static final String EUREKA_SERVER_URL = "http://localhost:8761";

	private static final List<String> DISCOVERABLE_SERVICES = Arrays.asList("api-gateway", "auth-service",
			"member-service", "resource-service", "order-service", "payment-service", "invoice-service",
			"notification-service");

	// auth-service is the only registered service with a non-root server.servlet.context-path
	// (/uaa - see its application.properties), so its actuator health lives under that prefix
	// too; every other service's Eureka-registered URI already points straight at its root.
	private static final Map<String, String> HEALTH_PATH_OVERRIDES = Collections.singletonMap("auth-service",
			"/uaa/actuator/health");

	private final DiscoveryClient discoveryClient;
	private final RestTemplate restTemplate;

	public DashboardController(DiscoveryClient discoveryClient, RestTemplate restTemplate) {
		this.discoveryClient = discoveryClient;
		this.restTemplate = restTemplate;
	}

	@GetMapping("/dashboard/services")
	public List<ServiceStatusResponse> serviceStatuses() {
		List<ServiceStatusResponse> statuses = new ArrayList<>();
		statuses.add(checkHealth("eureka-server", EUREKA_SERVER_URL, "/actuator/health"));
		for (String serviceId : DISCOVERABLE_SERVICES) {
			statuses.add(checkDiscoveredService(serviceId));
		}
		return statuses;
	}

	private ServiceStatusResponse checkDiscoveredService(String serviceId) {
		List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
		if (instances.isEmpty()) {
			return new ServiceStatusResponse(serviceId, "DOWN", null);
		}
		String healthPath = HEALTH_PATH_OVERRIDES.getOrDefault(serviceId, "/actuator/health");
		return checkHealth(serviceId, instances.get(0).getUri().toString(), healthPath);
	}

	private ServiceStatusResponse checkHealth(String name, String baseUrl, String healthPath) {
		try {
			ResponseEntity<ActuatorHealthResponse> response = restTemplate.getForEntity(baseUrl + healthPath,
					ActuatorHealthResponse.class);
			ActuatorHealthResponse body = response.getBody();
			boolean up = response.getStatusCode() == HttpStatus.OK && body != null && "UP".equals(body.getStatus());
			return new ServiceStatusResponse(name, up ? "UP" : "DOWN", baseUrl);
		} catch (RestClientException ex) {
			return new ServiceStatusResponse(name, "DOWN", baseUrl);
		}
	}
}
