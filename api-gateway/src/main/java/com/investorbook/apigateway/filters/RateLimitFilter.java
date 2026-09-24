package com.investorbook.apigateway.filters;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.investorbook.apigateway.dto.ErrorResponse;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import reactor.core.publisher.Mono;

/**
 * Caps how many requests a single client can make per second. Registered as a plain WebFilter
 * (see RateLimitConfig), not a Gateway GlobalFilter: a GlobalFilter only runs for requests
 * Gateway actually routes to a downstream service, never for this app's own locally-handled
 * endpoints (/login, /dashboard/**). A WebFilter runs for every request Netty serves, local or
 * routed.
 *
 * One resilience4j RateLimiter per client IP, created lazily and cached by RateLimiterRegistry.
 * timeoutDuration is zero: a request either gets a permit immediately or is rejected - it never
 * queues waiting for one.
 *
 * Known limitations, honestly: in-memory and per-instance, so it doesn't share state across a
 * horizontally-scaled deployment; the registry never evicts a client's limiter once created, so
 * memory grows with the number of distinct client IPs seen over the process lifetime; and keying
 * by remote address means every client behind the same NAT/proxy shares one bucket. See the
 * README's rate-limiting section for the full list - unchanged by this migration.
 */
public class RateLimitFilter implements WebFilter {

	private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

	private final RateLimiterRegistry registry;

	public RateLimitFilter(int requestsPerSecond) {
		RateLimiterConfig config = RateLimiterConfig.custom().limitForPeriod(requestsPerSecond)
				.limitRefreshPeriod(Duration.ofSeconds(1)).timeoutDuration(Duration.ZERO).build();
		this.registry = RateLimiterRegistry.of(config);
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String clientKey = clientKeyFor(exchange.getRequest());
		RateLimiter limiter = registry.rateLimiter(clientKey);

		if (limiter.acquirePermission()) {
			return chain.filter(exchange);
		}
		return reject(clientKey, exchange.getResponse());
	}

	private static String clientKeyFor(ServerHttpRequest request) {
		InetSocketAddress remoteAddress = request.getRemoteAddress();
		return remoteAddress == null ? "unknown" : remoteAddress.getAddress().getHostAddress();
	}

	private Mono<Void> reject(String clientKey, ServerHttpResponse response) {
		logger.warn("rate limit exceeded for client {}", sanitizeForLog(clientKey));

		response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		response.getHeaders().set(HttpHeaders.RETRY_AFTER, "1");

		byte[] body = toJson(new ErrorResponse("rate limit exceeded",
				"too many requests from this client, try again shortly")).getBytes(StandardCharsets.UTF_8);
		return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
	}

	private static String toJson(ErrorResponse body) {
		return "{\"timestamp\":\"%s\",\"message\":\"%s\",\"details\":\"%s\"}".formatted(body.timestamp(),
				body.message(), body.details());
	}

	// Strips CR/LF so a caller-controlled value can't forge extra log lines (CRLF/log injection).
	private static String sanitizeForLog(String value) {
		return value == null ? null : value.replaceAll("[\r\n]", "_");
	}
}
