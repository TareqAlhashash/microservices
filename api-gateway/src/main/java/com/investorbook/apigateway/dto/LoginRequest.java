package com.investorbook.apigateway.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A local, jakarta-validated copy of the /login form fields - not common's AuthRequest, which
 * still carries javax.validation constraints for the services on the old Boot 2.2.13 stack.
 * Those annotations are silently inert under Boot 4's jakarta-based validator, so reusing
 * AuthRequest here would look like it enforces username/password constraints without actually
 * doing so. Mutable, plain getters/setters rather than a record: Spring WebFlux binds
 * @ModelAttribute form parameters by calling setters on an instance it constructs itself.
 */
public class LoginRequest {

	@NotNull(message = "username cannot be null")
	private String username;

	@NotNull(message = "password cannot be null")
	@Size(min = 6, message = "password must be at least 6 char long")
	private String password;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
}
