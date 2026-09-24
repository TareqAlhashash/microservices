package com.investorbook.authservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.investorbook.authservice.dao.MemberRepository;
import com.investorbook.authservice.dao.entities.MemberEntity;
import com.investorbook.authservice.dto.LoginRequest;
import com.investorbook.authservice.dto.TokenResponse;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;

/**
 * Exercises the real /login endpoint end to end: real Postgres (so authentication actually goes
 * through MemberDetailsService and BCrypt, not a mock) and real RSA-signed JWT issuance. This is
 * the flow every other resource server in the repo depends on, so it is proven against the real
 * filter chain rather than unit tests of individual beans.
 *
 * Uses the RSA keypair already checked into application.properties (a demo secret, not a real
 * production key - see the README's honesty note about secrets management) so the issued token
 * is verifiable exactly the way a downstream resource server would verify it.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@AutoConfigureTestRestTemplate
@Testcontainers
class AuthServiceTokenIT {

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

	private void givenAMemberExists(String email, String rawPassword) {
		memberRepository.save(new MemberEntity(UUID.randomUUID().toString(), email, passwordEncoder.encode(rawPassword)));
	}

	private ResponseEntity<TokenResponse> login(String username, String password) {
		return restTemplate.postForEntity("/login", new LoginRequest(username, password), TokenResponse.class);
	}

	@Test
	void login_issuesAValidlySignedJwt_forCorrectCredentials() throws Exception {
		givenAMemberExists("jane@example.com", "correct-horse");

		ResponseEntity<TokenResponse> response = login("jane@example.com", "correct-horse");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		SignedJWT jwt = verifyAndParse(response.getBody().accessToken());
		assertThat(jwt.getJWTClaimsSet().getClaims()).containsEntry("user_name", "jane@example.com");
		assertThat(jwt.getJWTClaimsSet().getStringListClaim("authorities")).contains("ROLE_MEMBER");
	}

	private static SignedJWT verifyAndParse(String accessToken) throws Exception {
		byte[] der = Base64.getDecoder()
				.decode(PUBLIC_KEY.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", ""));
		RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
		SignedJWT jwt = SignedJWT.parse(accessToken);
		assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
		return jwt;
	}

	@Test
	void login_rejectsAWrongPassword_with401() {
		givenAMemberExists("wrong-pass@example.com", "correct-horse");

		ResponseEntity<TokenResponse> response = login("wrong-pass@example.com", "totally-wrong");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void login_rejectsAnUnknownUser_with401() {
		ResponseEntity<TokenResponse> response = login("nobody@example.com", "whatever");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void actuatorHealth_isReachableWithoutAToken() {
		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
	}
}
