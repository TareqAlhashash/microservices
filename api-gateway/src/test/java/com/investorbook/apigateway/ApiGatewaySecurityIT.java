package com.investorbook.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.investorbook.apigateway.proxy.OauthServiceProxy;
import com.investorbook.common.dto.AuthResponse;

import java.util.Collections;

/**
 * Proves api-gateway's edge security for real: everything not on the
 * ignored-path allowlist requires a valid JWT (real Spring Security filter
 * chain, not just the annotation), while /login is reachable without one so
 * a client can obtain a token in the first place. OauthServiceProxy (the
 * Feign call to auth-service) is mocked - auth-service isn't running in
 * this test, and it's a genuine external boundary; everything else (the
 * real HTTP round trip, LoginService's Basic-auth header assembly, the
 * security filter chain) is real.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "eureka.client.enabled=false",
		"security.oauth2.resource.jwt.key-value=test-signing-secret-please-ignore" })
class ApiGatewaySecurityIT {

	private static final String SIGNING_KEY = "test-signing-secret-please-ignore";

	@Autowired
	private TestRestTemplate restTemplate;

	@MockBean
	private OauthServiceProxy oauthServiceProxy;

	private static String tokenFor(String username) {
		JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
		converter.setSigningKey(SIGNING_KEY);

		OAuth2Request request = new OAuth2Request(Collections.emptyMap(), "html5", Collections.emptyList(), true,
				Collections.singleton("read"), Collections.emptySet(), null, Collections.emptySet(),
				Collections.emptyMap());
		UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(username, null,
				AuthorityUtils.createAuthorityList("ROLE_MEMBER"));
		OAuth2Authentication authentication = new OAuth2Authentication(request, userAuth);

		OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken("placeholder");
		return converter.enhance(accessToken, authentication).getValue();
	}

	@Test
	void anArbitraryRoute_isRejectedWithoutAToken() {
		ResponseEntity<String> response = restTemplate.getForEntity("/member-service/member", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anArbitraryRoute_isNotRejectedByGatewaySecurity_whenBearingAValidToken() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenFor("jane@example.com"));

		ResponseEntity<String> response = restTemplate.exchange("/member-service/member", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		// Eureka is disabled in this test, so Zuul can't resolve member-service and
		// the request fails downstream of the gateway's own security filter - the
		// point being proven here is that it is NOT rejected at 401 by this service.
		assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginPath_bypassesGatewaySecurity_evenWithoutAToken() {
		when(oauthServiceProxy.login(any(), any()))
				.thenReturn(new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti"));

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", "jane@example.com");
		form.add("password", "correct-horse");

		ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/login", new HttpEntity<>(form, headers),
				AuthResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getAccessToken()).isEqualTo("access");
	}

	/**
	 * AuthRequest's @NotNull/@Size constraints only bite now that login() takes
	 * @Valid - proves both that validation actually runs and that the shared
	 * CustomizedResponseEntityExceptionHandler (imported into
	 * ApiGatewayApplication) produces its usual error shape for it, same as any
	 * other validation failure in the system.
	 */
	@Test
	void login_rejectsAMissingPassword_with400() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", "jane@example.com");

		ResponseEntity<String> response = restTemplate.postForEntity("/login", new HttpEntity<>(form, headers),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("validation failed");
	}

	@Test
	void actuatorHealth_isReachableWithoutAToken() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
	}

	/*
	 * Deliberately not testing /uaa/oauth/token or /member-service/signup the
	 * same way as /login: both are Zuul-proxied routes with no real controller
	 * in this app, and with Eureka disabled (as it is in every test here) Zuul
	 * can't resolve them, which forwards internally to an ERROR dispatch that
	 * re-enters the secured "any request, authenticated" chain and comes back
	 * 401 for reasons unrelated to whether the original path is on the
	 * ignore-list. That's a real, worth-knowing quirk of testing Zuul routes
	 * without a running Eureka, not a gap in what /login already proves about
	 * the ignore-list mechanism itself.
	 */
}
