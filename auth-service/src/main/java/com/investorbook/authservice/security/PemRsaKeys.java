package com.investorbook.authservice.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Parses the RSA private key this repo has always kept in application.properties as a bare PEM
 * string (no line wrapping - the old spring-security-jwt library tolerated that;
 * java.security.KeyFactory doesn't care about wrapping either way since this strips all
 * whitespace before decoding). java.security.KeyFactory needs it in PKCS#8 form
 * ("BEGIN PRIVATE KEY"); the checked-in dev key was reformatted from PKCS#1
 * ("BEGIN RSA PRIVATE KEY") to PKCS#8 for this - same key material, just a different envelope -
 * since the JDK has no built-in PKCS#1 parser. Only the private key is parsed here: this service
 * only signs, never verifies, so it has no need of the public half - every other service already
 * has its own copy of that in its own application.properties.
 */
final class PemRsaKeys {

	private PemRsaKeys() {
	}

	static RSAPrivateKey parsePrivateKey(String pem) {
		byte[] der = decode(pem, "PRIVATE KEY");
		try {
			return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("investorbook.security.jwt.private.key is not a valid PKCS#8 RSA private key",
					e);
		}
	}

	private static byte[] decode(String pem, String label) {
		String base64 = pem.replace("-----BEGIN " + label + "-----", "").replace("-----END " + label + "-----", "")
				.replaceAll("\\s", "");
		return Base64.getDecoder().decode(base64);
	}
}
