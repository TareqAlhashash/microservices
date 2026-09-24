package com.investorbook.authservice.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.authservice.dto.LoginRequest;
import com.investorbook.authservice.dto.TokenResponse;
import com.investorbook.authservice.service.LoginService;

@RestController
public class LoginController {

	private final LoginService loginService;

	public LoginController(LoginService loginService) {
		this.loginService = loginService;
	}

	@PostMapping("/login")
	public TokenResponse login(@RequestBody LoginRequest request) {
		return loginService.login(request);
	}
}
