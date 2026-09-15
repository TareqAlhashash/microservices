package com.investorbook.memberservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.investorbook.common.dto.AuthRequest;
import com.investorbook.common.dto.AuthResponse;
import com.investorbook.memberservice.proxy.AuthenticationServiceProxy;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Proves the circuit breaker configured for AuthenticationServiceClient
 * (resilience4j.circuitbreaker.instances.authenticationService in
 * application.properties: slidingWindowSize=4, minimumNumberOfCalls=4,
 * failureRateThreshold=50) actually opens after repeated failures and then
 * short-circuits without calling the failing dependency again - the real
 * point of a circuit breaker, not just "a failure returns a fallback".
 * Needs the real Spring-managed, AOP-woven bean, so this can't be a plain
 * unit test; uses a real Postgres via Testcontainers only because
 * MemberServiceApplication's context requires a DataSource to start.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
@Testcontainers
class AuthenticationServiceCircuitBreakerIT {

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private AuthenticationServiceClient authenticationServiceClient;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@MockBean
	private AuthenticationServiceProxy authenticationServiceProxy;

	@BeforeEach
	void resetCircuitBreaker() {
		circuitBreakerRegistry.circuitBreaker("authenticationService").reset();
	}

	@Test
	void repeatedFailures_openTheCircuit_thenShortCircuitsWithoutCallingTheDownstreamProxyAgain() {
		when(authenticationServiceProxy.login(any())).thenThrow(new RuntimeException("auth-service unreachable"));
		AuthRequest request = new AuthRequest("jane@example.com", "correct-horse");

		for (int i = 0; i < 4; i++) {
			ResponseEntity<?> response = authenticationServiceClient.login(request);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		}
		verify(authenticationServiceProxy, times(4)).login(any());

		ResponseEntity<?> afterCircuitOpened = authenticationServiceClient.login(request);

		assertThat(afterCircuitOpened.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		verify(authenticationServiceProxy, times(4)).login(any());
	}

	@Test
	void aSuccessfulCall_passesThroughTheRealResponse_circuitStaysClosed() {
		AuthResponse token = new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti");
		when(authenticationServiceProxy.login(any())).thenReturn(ResponseEntity.ok(token));

		ResponseEntity<?> response = authenticationServiceClient
				.login(new AuthRequest("jane@example.com", "correct-horse"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(token);
	}
}
