package com.investorbook.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.investorbook.apigateway.filters.RateLimitFilter;

/**
 * Registers RateLimitFilter as a WebFilter bean, ordered right after CorsConfig's CorsWebFilter
 * (HIGHEST_PRECEDENCE) and ahead of Spring Security's reactive filter chain - see
 * RateLimitFilter's Javadoc for why it has to be a WebFilter rather than a Gateway GlobalFilter.
 * Ordered right after CORS specifically so a rejected (429) request to a browser client still
 * carries the CORS headers CorsWebFilter adds.
 */
@Configuration
public class RateLimitConfig {

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	public RateLimitFilter rateLimitFilter(
			@Value("${investorbook.security.rate-limit.requests-per-second:20}") int requestsPerSecond) {
		return new RateLimitFilter(requestsPerSecond);
	}
}
