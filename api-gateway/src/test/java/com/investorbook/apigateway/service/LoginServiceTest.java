package com.investorbook.apigateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.investorbook.apigateway.dto.AuthResponse;
import com.investorbook.apigateway.dto.LoginRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Stubs WebClient at the ExchangeFunction seam, same as DashboardServiceTest - a single
 * functional interface (request in, Mono<ClientResponse> out) rather than mocking WebClient's own
 * fluent builder chain method by method.
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	@Mock
	private ReactiveDiscoveryClient discoveryClient;

	@Mock
	private ServiceInstance authServiceInstance;

	private ClientRequest capturedRequest;

	private LoginService loginService;

	@BeforeEach
	void setUp() {
		when(discoveryClient.getInstances("auth-service")).thenReturn(Flux.just(authServiceInstance));
		when(authServiceInstance.getUri()).thenReturn(URI.create("http://localhost:9100"));

		WebClient webClient = WebClient.builder().exchangeFunction(request -> {
			capturedRequest = request;
			ClientResponse response = ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
					.body("{\"access_token\":\"access\"}").build();
			return Mono.just(response);
		}).build();
		loginService = new LoginService(discoveryClient, webClient);
	}

	private static LoginRequest loginRequest(String username, String password) {
		LoginRequest request = new LoginRequest();
		request.setUsername(username);
		request.setPassword(password);
		return request;
	}

	@Test
	void login_relaysTheIssuedAccessTokenBackToTheCaller() {
		AuthResponse response = loginService.login(loginRequest("jane@example.com", "correct-horse")).block();

		assertThat(response.accessToken()).isEqualTo("access");
	}

	@Test
	void login_targetsTheDiscoveredAuthServiceInstancesLoginEndpoint() {
		loginService.login(loginRequest("jane@example.com", "correct-horse")).block();

		assertThat(capturedRequest.url().toString()).isEqualTo("http://localhost:9100/uaa/login");
	}
}
