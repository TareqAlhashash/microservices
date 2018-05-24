package com.investorbook.common.util;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class EncryptionUtil {

	private static final int BITS_128_IN_BYTES = 16;

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	public static byte[] createSalt() {

		byte[] salt = new byte[BITS_128_IN_BYTES];
		SECURE_RANDOM.nextBytes(salt);

		return salt;
	}

	/**
	 * base 64 encoding
	 */

	public static byte[] encode(final byte[] input) {
		return Base64.getEncoder().encode(input);
	}

	/**
	 * base 64 decoding
	 */

	public static byte[] decode(final byte[] input) {
		return Base64.getDecoder().decode(input);
	}

	/**
	 * Perform a one-way hash of the String and prepend the salt.
	 */

	public static byte[] hash(String toHash) {

		return hash(toHash, createSalt());
	}

	private static byte[] hash(String toHash, byte[] salt) {

		// DO NOT support 'if already hashed, do not hash again': we don't want to
		// accidentally
		// allow authentication using pre-hashed passwords!

		try {
			// Salt the toHash, and hash it...

			PBEKeySpec spec = new PBEKeySpec(toHash.toCharArray(), salt, 1_000, 512);
			byte[] hashed;

			SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
			hashed = skf.generateSecret(spec).getEncoded();

			// ...then prepend the hash

			byte[] saltWithHashed = new byte[salt.length + hashed.length + 1];
			System.arraycopy(salt, 0, saltWithHashed, 0, salt.length);
			System.arraycopy(hashed, 0, saltWithHashed, salt.length, hashed.length);

			return saltWithHashed;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * return the hash of the password received
	 */
	public static byte[] hashToMatch(String toHash, byte[] existing) {

		byte[] salt = new byte[BITS_128_IN_BYTES];
		System.arraycopy(existing, 0, salt, 0, salt.length);
		return hash(toHash, salt);

	}
}
