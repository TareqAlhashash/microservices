package com.investorbook.apigateway.service;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RestController;

import com.investorbook.apigateway.config.JwtAuthenticationConfig;
import com.investorbook.apigateway.dto.AuthRequest;
import com.investorbook.apigateway.dto.AuthResponse;
import com.investorbook.apigateway.proxy.OauthServiceProxy;

@RestController
public class LoginService {

	@Autowired
	private OauthServiceProxy oauthServiceProxy;
	@Autowired
	JwtAuthenticationConfig config;

	@Bean
	public JwtAuthenticationConfig jwtConfig() {
		return new JwtAuthenticationConfig();
	}

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public AuthResponse login(AuthRequest authRequest) {
		authRequest.setGrantType("password");
		String encodedAuth = Base64.getEncoder()
				.encodeToString((config.getHtml5ClientId() + ":" + config.getHtml5ClientSecret()).getBytes());
		return oauthServiceProxy.login("Basic " + encodedAuth, authRequest.toFormParams());
	}
}
