package com.investorbook.common.observation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

/**
 * Keeps infrastructure chatter out of traces and HTTP metrics: health checks and Prometheus
 * scrapes hitting a service's own /actuator, and the Eureka client's registration, heartbeat and
 * registry-fetch calls. Left in, they bury the traces of real requests. The check is on
 * "contains" because auth-service serves everything under a /uaa context path.
 */
@Configuration
public class InfrastructureObservationConfiguration {

	@Bean
	public ObservationPredicate ignoreInfrastructureTraffic() {
		return (name, context) -> !isInfrastructureTraffic(context);
	}

	private static boolean isInfrastructureTraffic(Observation.Context context) {
		if (context instanceof ServerRequestObservationContext) {
			ServerRequestObservationContext server = (ServerRequestObservationContext) context;
			return server.getCarrier().getRequestURI().contains("/actuator");
		}
		if (context instanceof ClientRequestObservationContext) {
			ClientRequestObservationContext client = (ClientRequestObservationContext) context;
			return client.getCarrier().getURI().getPath().startsWith("/eureka");
		}
		return false;
	}
}
