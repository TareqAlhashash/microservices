package com.investorbook.apigateway.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.apigateway.dto.AuthResponse;
import com.investorbook.apigateway.dto.LoginRequest;
import com.investorbook.apigateway.service.LoginService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
public class LoginController {

	private final LoginService loginService;

	public LoginController(LoginService loginService) {
		this.loginService = loginService;
	}

	// @ModelAttribute, not an implicit unannotated parameter: WebFlux (unlike Spring MVC) needs
	// it stated explicitly to bind form fields onto a validated POJO.
	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public Mono<ResponseEntity<AuthResponse>> login(@Valid @ModelAttribute LoginRequest request) {
		return loginService.login(request).map(ResponseEntity::ok);
	}
}
