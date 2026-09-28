package com.investorbook.invoiceservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This service has no REST API, only Kafka listeners. Spring Security arrives transitively via
 * common and, without this chain, Boot's default only leaves /actuator/health and /actuator/info
 * open, so Prometheus's scrape of /actuator/prometheus would get a 401.
 */
@Configuration
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**").permitAll().anyRequest().denyAll())
				.build();
	}
}
