package com.investorbook.apigateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.investorbook.apigateway.dto.AuthResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Proves api-gateway's edge security for real: everything not on the permitAll allowlist
 * requires a valid JWT (the real reactive Spring Security filter chain, not just the DSL config),
 * while /login is reachable without one so a client can obtain a token in the first place.
 * auth-service isn't running in this test, so the call LoginService makes to it is mocked at the
 * two seams that reach outside this process: ReactiveDiscoveryClient (a spy on the real bean, so
 * every OTHER lookup - notably order-service's, which
 * anArbitraryRoute_isNotRejectedByGatewaySecurity... below depends on resolving to nothing, same
 * as production with Eureka disabled - still behaves exactly as it does with no test involved)
 * and the WebClient it's called through (a stub ExchangeFunction - StubWebClientConfig).
 * Everything else (the real HTTP round trip, the security filter chain) is real.
 *
 * Mints its own RSA keypair rather than reusing a fixed test secret: the modern
 * NimbusReactiveJwtDecoder only verifies asymmetric (RSA) signatures, unlike the old
 * spring-security-oauth2 resource server this replaced, which could be pointed at a plain HMAC
 * secret for a quick test override.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@AutoConfigureTestRestTemplate
class ApiGatewaySecurityIT {

	private static final KeyPair KEY_PAIR = generateRsaKeyPair();

	@Autowired
	private TestRestTemplate restTemplate;

	// A spy, not a wholesale mock/replacement bean: the real bean here is already @Primary
	// (Spring Cloud's reactiveCompositeDiscoveryClient), so a second bean of this type marked
	// @Primary to take its place is ambiguous rather than an override. A spy wraps that same
	// bean in place instead, real behaviour untouched except for the one lookup stubbed below.
	@MockitoSpyBean
	private ReactiveDiscoveryClient discoveryClient;

	@BeforeEach
	void stubAuthServiceDiscovery() {
		ServiceInstance authServiceInstance = mock(ServiceInstance.class);
		when(authServiceInstance.getUri()).thenReturn(URI.create("http://auth-service.test"));
		doReturn(Flux.just(authServiceInstance)).when(discoveryClient).getInstances(eq("auth-service"));
	}

	@DynamicPropertySource
	static void jwtPublicKey(DynamicPropertyRegistry registry) {
		registry.add("investorbook.security.jwt.public.key", ApiGatewaySecurityIT::publicKeyPem);
	}

	// The stub ReactiveDiscoveryClient above resolves "auth-service" to a URI nothing is actually
	// listening on - this WebClient never opens a real connection, so that's fine; it always
	// answers with the same canned token response regardless of the request.
	@TestConfiguration
	static class StubWebClientConfig {

		@Bean
		@Primary
		WebClient stubInternalWebClient() {
			return WebClient.builder().exchangeFunction(request -> {
				ClientResponse response = ClientResponse.create(HttpStatus.OK)
						.header("Content-Type", "application/json").body("{\"access_token\":\"access\"}").build();
				return Mono.just(response);
			}).build();
		}
	}

	private static KeyPair generateRsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static String publicKeyPem() {
		String base64 = Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
		return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
	}

	private static String tokenFor(String username) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(username).claim("user_name", username)
				.claim("authorities", List.of("ROLE_MEMBER")).issueTime(Date.from(Instant.now()))
				.expirationTime(Date.from(Instant.now().plusSeconds(3600))).build();
		SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
		signedJwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
		return signedJwt.serialize();
	}

	@Test
	void anArbitraryRoute_isRejectedWithoutAToken() {
		ResponseEntity<String> response = restTemplate.getForEntity("/order-service/orders", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anArbitraryRoute_isNotRejectedByGatewaySecurity_whenBearingAValidToken() throws Exception {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(tokenFor("jane@example.com"));

		ResponseEntity<String> response = restTemplate.exchange("/order-service/orders", HttpMethod.GET,
				new HttpEntity<>(headers), String.class);

		// Eureka is disabled in this test, so Gateway can't resolve order-service and the
		// request fails downstream of the gateway's own security filter chain - the point being
		// proven here is that it is NOT rejected at 401 by this service.
		assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void loginPath_bypassesGatewaySecurity_evenWithoutAToken() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("username", "jane@example.com");
		form.add("password", "correct-horse");

		ResponseEntity<AuthResponse> response = restTemplate.postForEntity("/login", new HttpEntity<>(form, headers),
				AuthResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().accessToken()).isEqualTo("access");
	}

	/**
	 * LoginRequest's jakarta.validation constraints only bite because /login binds it with
	 * @Valid @ModelAttribute - proves both that validation actually runs and that
	 * WebFluxExceptionHandler produces the usual {timestamp, message, details} error shape for
	 * it, same as any other validation failure in the system.
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
}
