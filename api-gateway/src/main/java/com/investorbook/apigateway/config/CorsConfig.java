package com.investorbook.apigateway.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * This is the one service browser clients (the React storefront) call directly, so it's the only
 * service that needs a CORS policy. Registered as its own WebFilter, ordered ahead of Spring
 * Security's reactive filter chain (Ordered.HIGHEST_PRECEDENCE) for the same reason the old
 * servlet-Filter version was: a permitAll() route still passes through the security filter
 * chain, but ordering CORS first means its headers are already on the response before anything
 * later in the chain (including a RateLimitFilter rejection) runs.
 */
@Configuration
public class CorsConfig {

	@Value("${investorbook.security.cors.allowed-origins}")
	private String allowedOrigins;

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public CorsWebFilter corsWebFilter() {
		CorsConfiguration configuration = new CorsConfiguration();
		List<String> origins = Arrays.asList(allowedOrigins.split(","));
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return new CorsWebFilter(source);
	}
}
