package com.investorbook.apigateway.service;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
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

	@Autowired
	private OauthServiceProxy oauthServiceProxy;

	@Autowired
	private JwtAuthenticationConfig config;

	@Bean
	public JwtAuthenticationConfig jwtConfig() {
		return new JwtAuthenticationConfig();
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public ResponseEntity<AuthResponse> login(AuthRequest authRequest) {
		authRequest.setGrantType("password");
		String encodedAuth = Base64.getEncoder()
				.encodeToString((config.getHtml5ClientId() + ":" + config.getHtml5ClientSecret()).getBytes());
		return ResponseEntity.ok(oauthServiceProxy.login("Basic " + encodedAuth, authRequest.toFormParams()));
	}
}
