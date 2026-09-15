package com.investorbook.apigateway.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.validation.Valid;

import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.apigateway.config.JwtAuthenticationConfig;
import com.investorbook.apigateway.proxy.OauthServiceProxy;
import com.investorbook.common.dto.AuthRequest;
import com.investorbook.common.dto.AuthResponse;

@RestController
public class LoginService {

	private final OauthServiceProxy oauthServiceProxy;

	private final JwtAuthenticationConfig config;

	public LoginService(OauthServiceProxy oauthServiceProxy, JwtAuthenticationConfig config) {
		this.oauthServiceProxy = oauthServiceProxy;
		this.config = config;
	}

	@Bean
	public static JwtAuthenticationConfig jwtConfig() {
		return new JwtAuthenticationConfig();
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public ResponseEntity<AuthResponse> login(@Valid AuthRequest authRequest) {
		authRequest.setGrantType("password");
		String encodedAuth = Base64.getEncoder().encodeToString(
				(config.getHtml5ClientId() + ":" + config.getHtml5ClientSecret()).getBytes(StandardCharsets.UTF_8));
		return ResponseEntity.ok(oauthServiceProxy.login("Basic " + encodedAuth, authRequest.toFormParams()));
	}
}
