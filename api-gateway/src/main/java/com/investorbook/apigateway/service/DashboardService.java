package com.investorbook.apigateway.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.investorbook.apigateway.dto.ActuatorHealthResponse;
import com.investorbook.apigateway.dto.ServiceStatusResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Aggregates health across every service in this system for the frontend's dashboard - a
 * browser can't reach any of them directly (each is a different origin/port, and only
 * api-gateway has a CORS policy, see CorsConfig), so this app calls each one server-to-server
 * instead.
 *
 * Checks run concurrently (Flux.merge), not sequentially - a genuine improvement the WebClient
 * migration made easy: the old blocking RestTemplate version checked each service one after
 * another, so one slow/dead service delayed every check behind it, not just its own timeout.
 */
@Service
public class DashboardService {

	// eureka-server can't be discovered through itself (it never registers as a client -
	// eureka.client.register-with-eureka=false), so it's the one hardcoded address here;
	// every other service below is resolved live through Eureka, not a hardcoded port.
	private static final String EUREKA_SERVER_URL = "http://localhost:8761";

	private static final List<String> DISCOVERABLE_SERVICES = Arrays.asList("api-gateway", "auth-service",
			"order-service", "payment-service", "invoice-service", "notification-service");

	// auth-service is the only registered service with a non-root server.servlet.context-path
	// (/uaa - see its application.properties), so its actuator health lives under that prefix
	// too; every other service's Eureka-registered URI already points straight at its root.
	private static final Map<String, String> HEALTH_PATH_OVERRIDES = Collections.singletonMap("auth-service",
			"/uaa/actuator/health");

	private final ReactiveDiscoveryClient discoveryClient;
	private final WebClient webClient;

	public DashboardService(ReactiveDiscoveryClient discoveryClient, WebClient internalWebClient) {
		this.discoveryClient = discoveryClient;
		this.webClient = internalWebClient;
	}

	public Mono<List<ServiceStatusResponse>> serviceStatuses() {
		Mono<ServiceStatusResponse> eureka = checkHealth("eureka-server", EUREKA_SERVER_URL, "/actuator/health");
		Flux<ServiceStatusResponse> discovered = Flux.fromIterable(DISCOVERABLE_SERVICES)
				.flatMap(this::checkDiscoveredService);
		return Flux.concat(eureka.flux(), discovered).collectList();
	}

	private Mono<ServiceStatusResponse> checkDiscoveredService(String serviceId) {
		return discoveryClient.getInstances(serviceId).next()
				.flatMap(instance -> checkHealth(serviceId, instance.getUri().toString(),
						HEALTH_PATH_OVERRIDES.getOrDefault(serviceId, "/actuator/health")))
				.defaultIfEmpty(new ServiceStatusResponse(serviceId, "DOWN", null));
	}

	private Mono<ServiceStatusResponse> checkHealth(String name, String baseUrl, String healthPath) {
		return webClient.get().uri(baseUrl + healthPath).retrieve().bodyToMono(ActuatorHealthResponse.class)
				.map(body -> new ServiceStatusResponse(name, "UP".equals(body.getStatus()) ? "UP" : "DOWN", baseUrl))
				.onErrorReturn(new ServiceStatusResponse(name, "DOWN", baseUrl));
	}
}
