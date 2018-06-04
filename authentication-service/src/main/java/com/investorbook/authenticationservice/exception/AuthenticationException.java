package com.investorbook.authenticationservice.exception;

/**
 * just for marking a type. exception response handled in the
 * signupresponseEntityExceptionHandler
 * 
 * @author tareq
 *
 */
public class AuthenticationException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public AuthenticationException(String message) {
		super(message);
	}
}
