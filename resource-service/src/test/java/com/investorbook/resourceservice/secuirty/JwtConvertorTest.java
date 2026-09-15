package com.investorbook.resourceservice.secuirty;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.provider.OAuth2Authentication;

class JwtConvertorTest {

	private final JwtConvertor jwtConvertor = new JwtConvertor();

	/**
	 * JwtUtil (in the common lib, and its resource-service equivalents) reads
	 * claims off Authentication.getDetails() as the raw decoded JWT map - this
	 * override is the only thing that makes that map available, so it has to
	 * survive extractAuthentication unchanged.
	 */
	@Test
	void extractAuthentication_attachesTheRawClaimsMapAsAuthenticationDetails() {
		Map<String, Object> claims = new HashMap<>();
		claims.put("user_name", "jane@example.com");
		claims.put("scope", Collections.singletonList("read"));
		claims.put("authorities", Collections.singletonList("ROLE_MEMBER"));

		OAuth2Authentication authentication = jwtConvertor.extractAuthentication(claims);

		assertThat(authentication.getDetails()).isSameAs(claims);
		assertThat(authentication.getName()).isEqualTo("jane@example.com");
	}
}
