package com.investorbook.common.exception;

import java.util.Date;

public class ExceptionResponse {

	private Date timestamp;
	private String message;
	private String details;

	public ExceptionResponse(Date timestamp, String message, String details) {
		super();
		this.timestamp = timestamp == null ? null : new Date(timestamp.getTime());
		this.message = message;
		this.details = details;
	}

	public Date getTimestamp() {
		return timestamp == null ? null : new Date(timestamp.getTime());
	}

	public String getMessage() {
		return message;
	}

	public String getDetails() {
		return details;
	}

}
