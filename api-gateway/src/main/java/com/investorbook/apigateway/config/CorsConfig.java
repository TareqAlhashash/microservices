package com.investorbook.apigateway.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * This is the one service browser clients (the React storefront) call directly, so it's the
 * only service that needs a CORS policy - every other module is only ever called
 * server-to-server or proxied through here. Allowed origins are configurable since the
 * frontend's dev server (Vite) and a real deployment's origin won't be the same.
 *
 * Registered as a plain servlet Filter (not via HttpSecurity.cors()) ordered ahead of Spring
 * Security's own filter chain: SecurityConfiguration's WebSecurity.ignoring() makes the public
 * catalog route (/order-service/products/**) bypass that chain entirely, which would also skip
 * a CORS filter wired through HttpSecurity - a standalone Filter at HIGHEST_PRECEDENCE runs for
 * every request regardless, including ones Spring Security never sees.
 */
@Configuration
public class CorsConfig {

	@Value("${investorbook.security.cors.allowed-origins}")
	private String allowedOrigins;

	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter() {
		CorsConfiguration configuration = new CorsConfiguration();
		List<String> origins = Arrays.asList(allowedOrigins.split(","));
		configuration.setAllowedOrigins(origins);
		configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);

		FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}
}
