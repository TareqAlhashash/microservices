package com.investorbook.authservice.security;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Signs the JWT this service issues, directly with Nimbus JOSE - no OAuth2 authorization-server
 * framework involved. This app has exactly one client (api-gateway, on the browser's behalf) and
 * one grant type in practice (username + password), so the machinery an OAuth2 provider needs for
 * client registration, consent, multiple grant types, and token introspection/revocation buys
 * nothing here; it only added a custom grant-type extension to work around Spring Authorization
 * Server deliberately not implementing password grant. This class is the entire replacement:
 * build the claims, sign them, done - the same "user_name"/"authorities" claim shape every
 * resource server in this repo (order-service, api-gateway) already reads, so nothing downstream
 * had to change.
 */
@Component
public class JwtIssuer {

	private final RSASSASigner signer;
	private final long expirationSeconds;

	public JwtIssuer(@Value("${investorbook.security.jwt.private.key}") String privateKeyPem,
			@Value("${investorbook.security.jwt.expiration:86400}") long expirationSeconds) {
		this.signer = new RSASSASigner(PemRsaKeys.parsePrivateKey(privateKeyPem));
		this.expirationSeconds = expirationSeconds;
	}

	public String issue(String email, List<String> authorities) {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(email).claim("user_name", email)
				.claim("authorities", authorities).issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(expirationSeconds))).build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
		try {
			jwt.sign(signer);
		} catch (JOSEException e) {
			throw new IllegalStateException("failed to sign JWT", e);
		}
		return jwt.serialize();
	}
}
