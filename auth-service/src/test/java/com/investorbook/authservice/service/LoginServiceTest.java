package com.investorbook.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.authority.AuthorityUtils;

import com.investorbook.authservice.dto.LoginRequest;
import com.investorbook.authservice.dto.TokenResponse;
import com.investorbook.authservice.security.JwtIssuer;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	@Mock
	private DaoAuthenticationProvider authenticationProvider;

	@Mock
	private JwtIssuer jwtIssuer;

	private LoginService loginService;

	@BeforeEach
	void setUp() {
		loginService = new LoginService(authenticationProvider, jwtIssuer);
	}

	@Test
	void login_issuesATokenForTheAuthenticatedMember_withTheirGrantedAuthorities() {
		UsernamePasswordAuthenticationToken authenticated = UsernamePasswordAuthenticationToken.authenticated(
				"jane@example.com", null, AuthorityUtils.createAuthorityList("ROLE_MEMBER"));
		when(authenticationProvider.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenReturn(authenticated);
		when(jwtIssuer.issue("jane@example.com", List.of("ROLE_MEMBER"))).thenReturn("signed-jwt");

		TokenResponse response = loginService.login(new LoginRequest("jane@example.com", "correct-horse"));

		assertThat(response.accessToken()).isEqualTo("signed-jwt");
	}

	/**
	 * No try/catch here on purpose: the controller relies on this exception reaching Spring
	 * Security's ExceptionTranslationFilter unmodified so it becomes a 401, not a 500 - see
	 * common's CustomizedResponseEntityExceptionHandler.
	 */
	@Test
	void login_propagatesTheAuthenticationException_forBadCredentials() {
		when(authenticationProvider.authenticate(any(UsernamePasswordAuthenticationToken.class)))
				.thenThrow(new BadCredentialsException("Bad credentials"));

		assertThatThrownBy(() -> loginService.login(new LoginRequest("jane@example.com", "wrong")))
				.isInstanceOf(BadCredentialsException.class);
	}
}
