package com.investorbook.notificationservice.service;

/**
 * The customer's address can never receive mail (missing, malformed, or more
 * than one address). Unlike a mail-server outage, retrying cannot help, which
 * is why NotificationEventListener treats this as a saga failure to
 * compensate rather than as a best-effort send that is simply logged.
 */
public class UndeliverableRecipientException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UndeliverableRecipientException(String message) {
		super(message);
	}

	public UndeliverableRecipientException(String message, Throwable cause) {
		super(message, cause);
	}
}
