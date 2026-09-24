package com.investorbook.apigateway.dto;

import java.time.Instant;

/**
 * The {timestamp, message, details} shape every service in this system returns for an error,
 * originally common's ExceptionResponse. A local copy here: common's version extends Spring
 * MVC's (servlet-based) ResponseEntityExceptionHandler, which doesn't exist for WebFlux - see
 * WebFluxExceptionHandler.
 */
public record ErrorResponse(Instant timestamp, String message, String details) {

	public ErrorResponse(String message, String details) {
		this(Instant.now(), message, details);
	}
}
