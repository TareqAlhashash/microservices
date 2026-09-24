package com.investorbook.apigateway.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Reactive equivalent of the old @EnableResourceServer/WebSecurityConfigurerAdapter setup - see
 * the README/CLAUDE.md migration notes for why this moved off the legacy spring-security-oauth2
 * stack. Verifies the same RSA-signed JWTs auth-service always issued; nothing about the token
 * format changed, only how this app checks them.
 *
 * api-gateway has no method-level @PreAuthorize of its own (unlike order-service) - it only needs
 * "does this request carry a validly signed, unexpired JWT", so there's no authorities/roles
 * converter to configure here; fine-grained role checks happen in the proxied services
 * themselves.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

	@Bean
	SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
		return http
				// Stateless, bearer-token API - no cookie/session for a forged cross-site
				// request to ride along on, so there's nothing for CSRF protection to defend.
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(exchange -> exchange
						.pathMatchers("/login", "/actuator/**", "/order-service/products",
								"/order-service/products/**", "/dashboard/**")
						.permitAll().anyExchange().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtDecoder(jwtDecoder))).build();
	}

	@Bean
	ReactiveJwtDecoder jwtDecoder(@Value("${investorbook.security.jwt.public.key}") String publicKeyPem) {
		return NimbusReactiveJwtDecoder.withPublicKey(parseRsaPublicKey(publicKeyPem)).build();
	}

	private static RSAPublicKey parseRsaPublicKey(String pem) {
		String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
				.replaceAll("\\s", "");
		byte[] decoded = Base64.getDecoder().decode(base64);
		try {
			return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("investorbook.security.jwt.public.key is not a valid RSA public key", e);
		}
	}
}
