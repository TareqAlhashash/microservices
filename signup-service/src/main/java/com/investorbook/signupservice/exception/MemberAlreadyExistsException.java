package com.investorbook.signupservice.exception;

/**
 * just for marking a type. exception response handled in the
 * signupresponseEntityExceptionHandler
 * 
 * @author tareq
 *
 */
public class MemberAlreadyExistsException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public MemberAlreadyExistsException(String message) {
		super(message);
	}
}
