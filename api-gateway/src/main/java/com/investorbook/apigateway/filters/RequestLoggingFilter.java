package com.investorbook.apigateway.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Logs every request Gateway actually routes to a downstream service. A GlobalFilter only runs
 * for routed requests, never this app's own local endpoints (/login, /dashboard/**) - see
 * RateLimitFilter, which needs to cover those too and is registered as a plain WebFilter instead.
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

	private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		logger.info("request URL -> {}", sanitizeForLog(exchange.getRequest().getURI().getPath()));
		return chain.filter(exchange);
	}

	@Override
	public int getOrder() {
		return 1;
	}

	// Strips CR/LF so a caller-controlled URI can't forge extra log lines or corrupt log-file
	// structure (CRLF/log injection).
	private static String sanitizeForLog(String value) {
		return value == null ? null : value.replaceAll("[\r\n]", "_");
	}
}
