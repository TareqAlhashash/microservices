package com.investorbook.signupservice.exception;

/**
 * just for marking a type. exception response handled in the
 * signupresponseEntityExceptionHandler
 * 
 * @author tareq
 *
 */
public class MemberAlreadyExistsException extends RuntimeException {

	public MemberAlreadyExistsException(String message) {
		super(message);
	}

}
