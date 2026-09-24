package com.investorbook.apigateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;

import com.investorbook.apigateway.dto.ErrorResponse;

/**
 * WebFlux's own error handler for this app's own controllers (/login, /dashboard/services) - not
 * common's CustomizedResponseEntityExceptionHandler, which extends Spring MVC's (servlet-based)
 * ResponseEntityExceptionHandler and has no WebFlux equivalent under that name. Same
 * {timestamp, message, details} shape every other service in this system returns.
 *
 * Deliberately doesn't special-case AccessDeniedException/AuthenticationException the way
 * common's handler has to: those are raised by Spring Security's reactive WebFilter chain, which
 * runs entirely before a request reaches this app's own controllers or this advice - unlike the
 * old @PreAuthorize-via-AOP case, there's no shared exception-resolution machinery for a generic
 * handler to accidentally intercept ahead of security. api-gateway also has no method-level
 * @PreAuthorize of its own (see SecurityWebFilterChain) - fine-grained role checks live in the
 * proxied services, not here.
 *
 * Does special-case ErrorResponseException though - a real bug, not a hypothetical: Spring's own
 * routing machinery raises it (as NoResourceFoundException) for a request that matches no
 * handler, e.g. before spring-boot-starter-actuator was added, /actuator/health itself hit this
 * and the catch-all below turned that 404 into a 500 - the exact "generic handler masks a more
 * specific status" bug this repo's shared error handler was written to avoid in the first place.
 * Preserving the exception's own status code here is what closes that gap for WebFlux.
 */
@RestControllerAdvice
public class WebFluxExceptionHandler {

	@ExceptionHandler(WebExchangeBindException.class)
	public ResponseEntity<ErrorResponse> handleValidationFailure(WebExchangeBindException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse("validation failed", ex.getBindingResult().toString()));
	}

	@ExceptionHandler(ErrorResponseException.class)
	public ResponseEntity<ErrorResponse> handleErrorResponseException(ErrorResponseException ex) {
		String message = ex.getBody().getDetail() != null ? ex.getBody().getDetail() : ex.getStatusCode().toString();
		return ResponseEntity.status(ex.getStatusCode())
				.body(new ErrorResponse(message, ex.getClass().getSimpleName()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleAnyOtherException(Exception ex) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse(ex.getMessage(), ex.getClass().getSimpleName()));
	}
}
