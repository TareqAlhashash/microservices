package com.investorbook.apigateway.service;

import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.investorbook.apigateway.dto.AuthResponse;
import com.investorbook.apigateway.dto.LoginRequest;

import reactor.core.publisher.Mono;

/**
 * Forwards a login attempt to auth-service and relays its response back - the whole login use
 * case, kept out of LoginController so the controller stays a thin HTTP translation layer.
 */
@Service
public class LoginService {

	private final ReactiveDiscoveryClient discoveryClient;
	private final WebClient webClient;

	public LoginService(ReactiveDiscoveryClient discoveryClient, WebClient internalWebClient) {
		this.discoveryClient = discoveryClient;
		this.webClient = internalWebClient;
	}

	public Mono<AuthResponse> login(LoginRequest request) {
		return discoveryClient.getInstances("auth-service").next()
				.flatMap(instance -> webClient.post().uri(instance.getUri() + "/uaa/login").bodyValue(request)
						.retrieve().bodyToMono(AuthResponse.class));
	}
}
