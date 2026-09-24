package com.investorbook.apigateway.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RateLimitFilterTest {

	private static final WebFilterChain PASS_THROUGH = exchange -> Mono.empty();

	private static ServerWebExchange exchangeFrom(String ip) {
		MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health")
				.remoteAddress(new InetSocketAddress(ip, 12345)).build();
		return MockServerWebExchange.from(request);
	}

	@Test
	void aRequestUnderTheLimit_isPassedToTheChain() {
		RateLimitFilter filter = new RateLimitFilter(2);
		ServerWebExchange exchange = exchangeFrom("10.0.0.1");

		StepVerifier.create(filter.filter(exchange, PASS_THROUGH)).verifyComplete();

		// PASS_THROUGH never touches the response - a null status proves the filter delegated
		// rather than writing a rejection itself.
		assertThat(exchange.getResponse().getStatusCode()).isNull();
	}

	@Test
	void aRequestOverTheLimit_isRejectedWith429AndNeverReachesTheChain() {
		RateLimitFilter filter = new RateLimitFilter(1);

		filter.filter(exchangeFrom("10.0.0.2"), PASS_THROUGH).block(); // consumes the one permit
		ServerWebExchange rejected = exchangeFrom("10.0.0.2");

		StepVerifier.create(filter.filter(rejected, PASS_THROUGH)).verifyComplete();

		assertThat(rejected.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(rejected.getResponse().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
		assertThat(rejected.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
		MockServerHttpResponse response = (MockServerHttpResponse) rejected.getResponse();
		assertThat(response.getBodyAsString().block()).contains("rate limit exceeded");
	}

	@Test
	void separateClients_eachGetTheirOwnLimit() {
		RateLimitFilter filter = new RateLimitFilter(1);

		ServerWebExchange first = exchangeFrom("10.0.0.3");
		filter.filter(first, PASS_THROUGH).block();
		assertThat(first.getResponse().getStatusCode()).isNull();

		ServerWebExchange second = exchangeFrom("10.0.0.4"); // a different client - its own permit
		filter.filter(second, PASS_THROUGH).block();
		assertThat(second.getResponse().getStatusCode()).isNull();
	}
}
