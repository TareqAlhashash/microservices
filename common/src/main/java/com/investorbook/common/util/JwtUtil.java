package com.investorbook.common.util;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Pulls a claim off the authenticated caller's JWT - this is how a resource server identifies
 * "the current user" from a decoded token without a separate lookup. Every resource server in
 * this repo authenticates via Spring Security's modern OAuth2 resource-server support, where a
 * JWT-authenticated request's Authentication is a JwtAuthenticationToken wrapping the decoded Jwt.
 */
public class JwtUtil {

	private static final String USER_NAME = "user_name";

	public static Optional<String> getEmail(Authentication auth) {
		return getClaim(auth, USER_NAME);
	}

	public static Optional<String> getClaim(Authentication auth, String claim) {
		if (auth instanceof JwtAuthenticationToken jwtAuth) {
			return Optional.ofNullable(jwtAuth.getToken().getClaimAsString(claim));
		}
		return Optional.empty();
	}
}
