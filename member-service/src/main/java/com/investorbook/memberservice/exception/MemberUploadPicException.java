package com.investorbook.memberservice.exception;

/**
 * just for marking a type. exception response handled in the
 * signupresponseEntityExceptionHandler
 * 
 * @author tareq
 *
 */
public class MemberUploadPicException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public MemberUploadPicException(String message) {
		super(message);
	}

	public MemberUploadPicException(String message, Throwable cause) {
		super(message, cause);
	}
}
