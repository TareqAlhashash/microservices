package com.investorbook.apigateway.config;

import java.time.Duration;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * A plain RestTemplate for server-to-server calls this app makes itself, outside of Zuul's
 * proxying and outside the Feign clients (see DashboardController, which polls every
 * registered service's own /actuator/health). Short timeouts matter here specifically: a
 * dead service must not make the whole dashboard endpoint hang waiting on it.
 */
@Configuration
public class RestTemplateConfig {

	@Bean
	public RestTemplate dashboardRestTemplate(RestTemplateBuilder builder) {
		return builder.setConnectTimeout(Duration.ofMillis(1000)).setReadTimeout(Duration.ofMillis(1000)).build();
	}
}
