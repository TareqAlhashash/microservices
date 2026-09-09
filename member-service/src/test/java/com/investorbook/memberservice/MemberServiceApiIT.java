package com.investorbook.memberservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.investorbook.common.dto.AuthResponse;
import com.investorbook.memberservice.dto.Member;
import com.investorbook.memberservice.proxy.AuthenticationServiceProxy;

/**
 * Exercises the real HTTP + Spring Security resource-server chain that the
 * unit tests structurally cannot: an unauthenticated request has to actually
 * be rejected by the filter chain, and @PreAuthorize has to actually be
 * enforced, neither of which is exercised by calling the controller directly.
 *
 * AuthenticationServiceProxy (the Feign call to api-gateway's /login) is
 * mocked - api-gateway isn't running in this test, and it's a genuine
 * external boundary. Everything else - Postgres, Spring Security, JPA - is
 * real. Deliberately doesn't cover /member/pic: that path already has
 * dedicated unit and LocalStack coverage, and adding a third container here
 * would buy little beyond what's already proven.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { "eureka.client.enabled=false",
		"security.oauth2.resource.jwt.key-value=test-signing-secret-please-ignore" })
@Testcontainers
class MemberServiceApiIT {

	private static final String SIGNING_KEY = "test-signing-secret-please-ignore";

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@MockBean
	private AuthenticationServiceProxy authenticationServiceProxy;

	private static String tokenFor(String email) {
		JwtAccessTokenConverter converter = new JwtAccessTokenConverter();
		converter.setSigningKey(SIGNING_KEY);

		OAuth2Request request = new OAuth2Request(Collections.emptyMap(), "html5", Collections.emptyList(), true,
				Collections.singleton("read"), Collections.emptySet(), null, Collections.emptySet(),
				Collections.emptyMap());
		UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(email, null,
				AuthorityUtils.createAuthorityList("ROLE_MEMBER"));
		OAuth2Authentication authentication = new OAuth2Authentication(request, userAuth);

		OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken("placeholder");
		return converter.enhance(accessToken, authentication).getValue();
	}

	private static HttpEntity<Void> authorizedRequest(String email) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenFor(email));
		return new HttpEntity<>(headers);
	}

	/**
	 * Member.email and Member.password are @JsonProperty(access = WRITE_ONLY) -
	 * correct for keeping them out of responses, but it also means Jackson
	 * strips them if TestRestTemplate serializes a Member instance as the
	 * outgoing request body (same rule applies whichever direction Jackson is
	 * asked to write). A raw map is what an actual HTTP client's JSON body
	 * looks like, so that's what gets sent here instead.
	 */
	private static Map<String, Object> signupPayload(String email, String password) {
		Map<String, Object> payload = new HashMap<>();
		payload.put("email", email);
		payload.put("firstName", "Jane");
		payload.put("lastName", "Doe");
		payload.put("password", password);
		payload.put("about", "about me");
		Map<String, Object> address = new HashMap<>();
		address.put("line1", "1 Test St");
		address.put("suburb", "Testville");
		address.put("state", "QLD");
		address.put("zipCode", "4000");
		address.put("country", "AU");
		payload.put("address", address);
		return payload;
	}

	@Test
	void member_isRejectedWithoutAToken() {
		ResponseEntity<String> response = restTemplate.getForEntity("/member", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void member_returns404_forAValidTokenWithNoMatchingAccount() {
		ResponseEntity<String> response = restTemplate.exchange("/member", HttpMethod.GET,
				authorizedRequest("nobody@example.com"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void signupThenFetch_roundTripsARealMemberThroughPostgres() {
		when(authenticationServiceProxy.login(any())).thenReturn(
				ResponseEntity.ok(new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti")));

		ResponseEntity<AuthResponse> signupResponse = restTemplate.postForEntity("/signup",
				signupPayload("integration@example.com", "plaintext1"), AuthResponse.class);
		assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<Member> fetchResponse = restTemplate.exchange("/member", HttpMethod.GET,
				authorizedRequest("integration@example.com"), Member.class);

		assertThat(fetchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(fetchResponse.getBody().getFirstName()).isEqualTo("Jane");
	}

	@Test
	void signup_rejectsDuplicateEmail_with409() {
		when(authenticationServiceProxy.login(any())).thenReturn(
				ResponseEntity.ok(new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti")));
		Map<String, Object> payload = signupPayload("dup-api@example.com", "plaintext1");
		restTemplate.postForEntity("/signup", payload, AuthResponse.class);

		ResponseEntity<String> secondResponse = restTemplate.postForEntity("/signup", payload, String.class);

		assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}
}
