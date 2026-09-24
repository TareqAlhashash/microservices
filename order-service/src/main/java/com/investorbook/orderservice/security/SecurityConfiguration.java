package com.investorbook.orderservice.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Verifies the same RSA-signed JWTs auth-service always issued via NimbusJwtDecoder - the
 * servlet-side sibling of api-gateway's reactive NimbusReactiveJwtDecoder. Unlike api-gateway,
 * this service has method-level @PreAuthorize("hasRole('MEMBER')") checks, so the JWT's
 * "authorities" claim (a plain string array like ["ROLE_MEMBER"], already carrying the ROLE_
 * prefix - see auth-service's GrantedAuthorities) has to be mapped into Spring Security
 * GrantedAuthority objects with no additional prefix added, or hasRole('MEMBER') would look for
 * "ROLE_ROLE_MEMBER" and every request would be denied.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter)
			throws Exception {
		return http.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**", "/products", "/products/**")
						.permitAll().anyRequest().authenticated())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.build();
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthorityPrefix("");
		authoritiesConverter.setAuthoritiesClaimName("authorities");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return converter;
	}

	@Bean
	JwtDecoder jwtDecoder(@Value("${investorbook.security.jwt.public.key}") String publicKeyPem) {
		return NimbusJwtDecoder.withPublicKey(parseRsaPublicKey(publicKeyPem)).build();
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
