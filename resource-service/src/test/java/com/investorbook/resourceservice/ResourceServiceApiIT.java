package com.investorbook.resourceservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

/**
 * Proves the real Spring Security resource-server chain, not just the
 * @PreAuthorize expression in isolation: an unauthenticated request has to
 * actually be rejected by the filter chain (401), an authenticated request
 * missing ROLE_MEMBER has to actually be denied by method security (403),
 * and only a correctly-authorised request reaches the controller (200).
 *
 * Mints JWTs directly against a test signing key, matching the pattern used
 * in member-service's MemberServiceApiIT - this service has no database or
 * other real dependency to exercise via Testcontainers, so the value here is
 * entirely in the real HTTP + filter chain round trip.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "eureka.client.enabled=false",
		"security.oauth2.resource.jwt.key-value=test-signing-secret-please-ignore" })
class ResourceServiceApiIT {

	private static final String SIGNING_KEY = "test-signing-secret-please-ignore";

	@Autowired
	private TestRestTemplate restTemplate;

	private static String tokenFor(String username, String... authorities) {
		JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
		converter.setSigningKey(SIGNING_KEY);

		OAuth2Request request = new OAuth2Request(Collections.emptyMap(), "html5", Collections.emptyList(), true,
				Collections.singleton("read"), Collections.emptySet(), null, Collections.emptySet(),
				Collections.emptyMap());
		UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(username, null,
				AuthorityUtils.createAuthorityList(authorities));
		OAuth2Authentication authentication = new OAuth2Authentication(request, userAuth);

		OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken("placeholder");
		return converter.enhance(accessToken, authentication).getValue();
	}

	private static HttpEntity<Void> bearerRequest(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return new HttpEntity<>(headers);
	}

	@Test
	void hi_isRejectedWithoutAToken() {
		ResponseEntity<String> response = restTemplate.exchange("/hi", HttpMethod.GET, HttpEntity.EMPTY,
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void hi_isForbidden_forAnAuthenticatedUserWithoutTheMemberRole() {
		String token = tokenFor("no-role@example.com");

		ResponseEntity<String> response = restTemplate.exchange("/hi", HttpMethod.GET, bearerRequest(token),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void hi_succeeds_forAMemberWithTheCorrectRole() {
		String token = tokenFor("member@example.com", "ROLE_MEMBER");

		ResponseEntity<String> response = restTemplate.exchange("/hi", HttpMethod.GET, bearerRequest(token),
				String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo("hi");
	}
}
