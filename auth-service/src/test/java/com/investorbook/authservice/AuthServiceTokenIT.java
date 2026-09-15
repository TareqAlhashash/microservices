package com.investorbook.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.jwt.Jwt;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.jwt.crypto.sign.RsaVerifier;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.investorbook.authservice.dao.MemberRepository;
import com.investorbook.authservice.dao.entities.MemberEntity;

/**
 * Exercises the real OAuth2 password-grant token endpoint end to end: real
 * Postgres (so authentication actually goes through MemberDetailsService and
 * BCrypt, not a mock), real RSA-signed JWT issuance, and the authorization
 * server's own access rules (token_key is public, check_token requires a
 * trusted client). This is the flow every other resource server in the repo
 * depends on, so it is proven against the real filter chain rather than unit
 * tests of individual beans.
 *
 * Uses the RSA keypair already checked into application.properties (a demo
 * secret, not a real production key - see the README's honesty note about
 * secrets management) so the issued token is verifiable exactly the way a
 * downstream resource server would verify it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@Testcontainers
class AuthServiceTokenIT {

	private static final String CLIENT_ID = "html5";
	private static final String CLIENT_SECRET = "html5secretpass123";
	private static final String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA+GGXOyXKtJtMJyiWV0+8GG2htO0tPXXLgf7Kk5OfP4sXR2lfHd7sGmo1baMho+yAyyE7lay663PfHe0jVGOEHApHAorrxP88EE7pvlyjGYq7OviQmRaEDKStgdVyEKqX9Ho+5GkFr0PL6n7biGkTaTWn1VrxqOFQdmr/thwYi/L/ZLON6zWODW4A0nqW9EbF+cZPFWr96NGVULTfMzxYQk6bpDFn0odGn8caUXsrG5GC2M2Yj3efeFW6nCwXhlCUfDJhvzIbBWpktY+hSMKUNPhqkOILetqJuoEWX3zi1YfUarusC/1MKUPYN6vgoaLJnmqlXIE8c3pfnLpAGfHIwQIDAQAB-----END PUBLIC KEY-----";

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

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private String givenAMemberExists(String email, String rawPassword) {
		String id = UUID.randomUUID().toString();
		memberRepository.save(new MemberEntity(id, email, passwordEncoder.encode(rawPassword)));
		return id;
	}

	private ResponseEntity<String> requestToken(String clientId, String clientSecret, String username,
			String password) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(clientId, clientSecret);

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "password");
		form.add("username", username);
		form.add("password", password);

		return restTemplate.postForEntity("/oauth/token", new HttpEntity<>(form, headers), String.class);
	}

	@Test
	void tokenEndpoint_issuesAValidlySignedJwt_forCorrectCredentials() {
		givenAMemberExists("jane@example.com", "correct-horse");

		ResponseEntity<String> response = requestToken(CLIENT_ID, CLIENT_SECRET, "jane@example.com",
				"correct-horse");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"access_token\"");

		String accessToken = extractField(response.getBody(), "access_token");
		Jwt jwt = JwtHelper.decodeAndVerify(accessToken, new RsaVerifier(PUBLIC_KEY));
		assertThat(jwt.getClaims()).contains("\"user_name\":\"jane@example.com\"").contains("\"ROLE_MEMBER\"");
	}

	@Test
	void tokenEndpoint_rejectsAWrongPassword_with400InvalidGrant() {
		givenAMemberExists("wrong-pass@example.com", "correct-horse");

		ResponseEntity<String> response = requestToken(CLIENT_ID, CLIENT_SECRET, "wrong-pass@example.com",
				"totally-wrong");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("invalid_grant");
	}

	@Test
	void tokenEndpoint_rejectsAnUnknownUser_with400InvalidGrant() {
		ResponseEntity<String> response = requestToken(CLIENT_ID, CLIENT_SECRET, "nobody@example.com", "whatever");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).contains("invalid_grant");
	}

	/**
	 * Unlike a bad username/password (rejected by the OAuth2 token endpoint
	 * itself, with a JSON invalid_grant body), an unknown client is rejected by
	 * Spring Security's own HTTP Basic entry point before the request reaches
	 * OAuth2's error handling - so there is no JSON error body to assert on,
	 * only the 401 status.
	 */
	@Test
	void tokenEndpoint_rejectsAnUnknownClient_with401() {
		givenAMemberExists("client-check@example.com", "correct-horse");

		ResponseEntity<String> response = requestToken("not-a-real-client", "not-a-real-secret",
				"client-check@example.com", "correct-horse");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tokenKeyEndpoint_isPubliclyReadable_withoutAnyCredentials() {
		ResponseEntity<String> response = restTemplate.getForEntity("/oauth/token_key", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"value\"");
	}

	@Test
	void actuatorHealth_isReachableWithoutAToken() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
	}

	@Test
	void checkTokenEndpoint_rejectsRequestsWithNoClientCredentials() {
		givenAMemberExists("introspect@example.com", "correct-horse");
		String accessToken = extractField(
				requestToken(CLIENT_ID, CLIENT_SECRET, "introspect@example.com", "correct-horse").getBody(),
				"access_token");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("token", accessToken);

		ResponseEntity<String> response = restTemplate.postForEntity("/oauth/check_token",
				new HttpEntity<>(form, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void checkTokenEndpoint_confirmsAnActiveTokenForATrustedClient() {
		givenAMemberExists("introspect-ok@example.com", "correct-horse");
		String accessToken = extractField(
				requestToken(CLIENT_ID, CLIENT_SECRET, "introspect-ok@example.com", "correct-horse").getBody(),
				"access_token");

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.setBasicAuth(CLIENT_ID, CLIENT_SECRET);
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("token", accessToken);

		ResponseEntity<String> response = restTemplate.postForEntity("/oauth/check_token",
				new HttpEntity<>(form, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"user_name\":\"introspect-ok@example.com\"");
	}

	private static String extractField(String json, String field) {
		String marker = "\"" + field + "\":\"";
		int start = json.indexOf(marker) + marker.length();
		int end = json.indexOf('"', start);
		return json.substring(start, end);
	}
}
