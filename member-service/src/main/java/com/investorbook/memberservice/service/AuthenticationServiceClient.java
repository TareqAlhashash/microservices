package com.investorbook.memberservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.investorbook.common.dto.AuthRequest;
import com.investorbook.common.dto.AuthResponse;
import com.investorbook.memberservice.proxy.AuthenticationServiceProxy;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

/**
 * Wraps the signup flow's callback into api-gateway/auth-service with a
 * circuit breaker (config: resilience4j.circuitbreaker.instances.authenticationService
 * in application.properties) and a Feign-level timeout (feign.client.config.api-gateway.*),
 * so a slow or down auth path degrades gracefully instead of hanging or
 * retrying a dead dependency on every signup.
 */
@Service
public class AuthenticationServiceClient {

	private static final Logger logger = LoggerFactory.getLogger(AuthenticationServiceClient.class);

	private final AuthenticationServiceProxy authenticationServiceProxy;

	public AuthenticationServiceClient(AuthenticationServiceProxy authenticationServiceProxy) {
		this.authenticationServiceProxy = authenticationServiceProxy;
	}

	@CircuitBreaker(name = "authenticationService", fallbackMethod = "loginFallback")
	public ResponseEntity<AuthResponse> login(AuthRequest authRequest) {
		return authenticationServiceProxy.login(authRequest.toFormParams());
	}

	/**
	 * By the time this runs, MemberServiceController.signUpMember has already
	 * committed the new member row - an unreachable/slow auth-service shouldn't
	 * undo a successful signup. Responding 202 with no token tells the client
	 * their account exists but they need to log in separately, rather than a
	 * 500 that would wrongly imply signup itself failed.
	 */
	private ResponseEntity<AuthResponse> loginFallback(AuthRequest authRequest, Throwable throwable) {
		logger.warn("auth-service login unavailable during signup for {}: {}",
				sanitizeForLog(authRequest.getUsername()), sanitizeForLog(throwable.toString()));
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.body(new AuthResponse(null, null, null, null, null, null));
	}

	private static String sanitizeForLog(String value) {
		return value == null ? null : value.replaceAll("[\r\n]", "_");
	}
}
