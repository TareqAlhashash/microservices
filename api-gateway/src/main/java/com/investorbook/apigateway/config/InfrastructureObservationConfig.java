package com.investorbook.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

/**
 * Keeps infrastructure chatter out of traces and HTTP metrics: health checks and Prometheus
 * scrapes hitting the gateway's own /actuator, and the Eureka client's registration, heartbeat
 * and registry-fetch calls. Left in, they bury the traces of real requests. This is the reactive
 * twin of common's InfrastructureObservationConfiguration, which the gateway can't depend on (see
 * CLAUDE.md, "api-gateway").
 */
@Configuration
public class InfrastructureObservationConfig {

	@Bean
	ObservationPredicate ignoreInfrastructureTraffic() {
		return (name, context) -> !isInfrastructureTraffic(context);
	}

	private static boolean isInfrastructureTraffic(Observation.Context context) {
		if (context instanceof ServerRequestObservationContext) {
			ServerRequestObservationContext server = (ServerRequestObservationContext) context;
			return server.getCarrier().getURI().getPath().startsWith("/actuator");
		}
		if (context instanceof ClientRequestObservationContext) {
			ClientRequestObservationContext client = (ClientRequestObservationContext) context;
			return client.getCarrier().getURI().getPath().startsWith("/eureka");
		}
		return false;
	}
}
