package com.investorbook.common.util;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;

public class JwtUtil {

	private static final String USER_NAME = "user_name";

	public Optional<String> getEmail(Authentication auth) {
		return getClaim(auth, USER_NAME);
	}

	public Optional<String> getClaim(Authentication auth, String claim) {

		Object details = auth.getDetails();
		if (details instanceof OAuth2AuthenticationDetails) {
			OAuth2AuthenticationDetails oAuth2AuthenticationDetails = (OAuth2AuthenticationDetails) details;

			@SuppressWarnings("unchecked")
			Map<String, Object> decodedDetails = (Map<String, Object>) oAuth2AuthenticationDetails.getDecodedDetails();
			return Optional.of(decodedDetails.get(claim) + "");
		}

		return Optional.empty();

	}
}
