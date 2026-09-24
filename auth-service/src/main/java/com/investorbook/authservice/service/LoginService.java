package com.investorbook.authservice.service;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.investorbook.authservice.dto.LoginRequest;
import com.investorbook.authservice.dto.TokenResponse;
import com.investorbook.authservice.security.JwtIssuer;

/**
 * Authenticates a member's credentials and issues a JWT for them - the whole login use case,
 * kept out of LoginController so the controller stays a thin HTTP translation layer.
 */
@Service
public class LoginService {

	private final DaoAuthenticationProvider authenticationProvider;
	private final JwtIssuer jwtIssuer;

	public LoginService(DaoAuthenticationProvider authenticationProvider, JwtIssuer jwtIssuer) {
		this.authenticationProvider = authenticationProvider;
		this.jwtIssuer = jwtIssuer;
	}

	// Wrong/unknown credentials surface as 401: DaoAuthenticationProvider throws an
	// AuthenticationException, which common's CustomizedResponseEntityExceptionHandler
	// deliberately rethrows rather than mapping to 500, letting Spring Security's
	// ExceptionTranslationFilter turn it into the usual 401.
	public TokenResponse login(LoginRequest request) {
		Authentication authentication = authenticationProvider
				.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
		List<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
		return new TokenResponse(jwtIssuer.issue(authentication.getName(), authorities));
	}
}
