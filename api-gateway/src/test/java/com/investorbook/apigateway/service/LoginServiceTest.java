package com.investorbook.apigateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.investorbook.apigateway.config.JwtAuthenticationConfig;
import com.investorbook.apigateway.proxy.OauthServiceProxy;
import com.investorbook.common.dto.AuthRequest;
import com.investorbook.common.dto.AuthResponse;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

	@Mock
	private OauthServiceProxy oauthServiceProxy;

	private LoginService loginService;

	@BeforeEach
	void setUp() {
		JwtAuthenticationConfig config = new JwtAuthenticationConfig();
		config.setHtml5ClientId("html5");
		config.setHtml5ClientSecret("html5secretpass123");
		loginService = new LoginService(oauthServiceProxy, config);
	}

	/**
	 * The whole point of this endpoint is that the browser/mobile client never
	 * sees the OAuth client secret - it goes to /login with just a
	 * username/password, and this service is the only place that assembles the
	 * Basic-auth header the token endpoint actually requires.
	 */
	@Test
	void login_addsTheHtml5ClientBasicAuthHeader_andForwardsAPasswordGrant() {
		AuthResponse expectedResponse = new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti");
		when(oauthServiceProxy.login(any(), any())).thenReturn(expectedResponse);

		AuthRequest request = new AuthRequest("jane@example.com", "correct-horse");
		ResponseEntity<AuthResponse> response = loginService.login(request);

		assertThat(response.getBody()).isSameAs(expectedResponse);

		String expectedAuthHeader = "Basic " + Base64.getEncoder().encodeToString("html5:html5secretpass123".getBytes());
		verify(oauthServiceProxy).login(eq(expectedAuthHeader), any());
	}

	@Test
	void login_setsThePasswordGrantTypeOnTheOutgoingRequest() {
		when(oauthServiceProxy.login(any(), any()))
				.thenReturn(new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti"));

		loginService.login(new AuthRequest("jane@example.com", "correct-horse"));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, ?>> formCaptor = ArgumentCaptor.forClass(Map.class);
		verify(oauthServiceProxy).login(any(), formCaptor.capture());
		Map<String, ?> form = formCaptor.getValue();
		assertThat(form.get("grant_type")).isEqualTo("password");
		assertThat(form.get("username")).isEqualTo("jane@example.com");
	}
}
