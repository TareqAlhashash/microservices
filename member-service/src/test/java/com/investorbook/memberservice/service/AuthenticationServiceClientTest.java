package com.investorbook.memberservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.investorbook.common.dto.AuthRequest;
import com.investorbook.common.dto.AuthResponse;
import com.investorbook.memberservice.proxy.AuthenticationServiceProxy;

/**
 * Unit-level coverage of the success path (constructed directly, no Spring
 * context, so @CircuitBreaker is not woven here). The fallback behaviour and
 * the circuit actually opening after repeated failures are proven separately
 * by AuthenticationServiceCircuitBreakerIT, which needs the real AOP proxy -
 * testing the fallback through reflection here would just duplicate that
 * less honestly.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceClientTest {

	@Mock
	private AuthenticationServiceProxy authenticationServiceProxy;

	@Test
	void login_returnsTheProxyResponse_onSuccess() {
		AuthenticationServiceClient client = new AuthenticationServiceClient(authenticationServiceProxy);
		AuthResponse token = new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti");
		when(authenticationServiceProxy.login(any())).thenReturn(ResponseEntity.ok(token));

		ResponseEntity<AuthResponse> response = client.login(new AuthRequest("jane@example.com", "correct-horse"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isSameAs(token);
	}
}
