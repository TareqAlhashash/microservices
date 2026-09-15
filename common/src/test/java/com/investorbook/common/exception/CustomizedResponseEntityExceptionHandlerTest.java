package com.investorbook.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

class CustomizedResponseEntityExceptionHandlerTest {

	private final CustomizedResponseEntityExceptionHandler handler = new CustomizedResponseEntityExceptionHandler();

	@Test
	void handleAllExceptions_returns500_forAnOrdinaryException() {
		ResponseEntity<Object> response = handler.handleAllExceptions(new IllegalStateException("boom"),
				mock(WebRequest.class));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(((ExceptionResponse) response.getBody()).getMessage()).isEqualTo("boom");
	}

	/**
	 * The Exception.class catch-all above would otherwise intercept @PreAuthorize
	 * denials before Spring Security's own ExceptionTranslationFilter can turn
	 * them into a 403 - this must rethrow, not handle, the exception. Found by
	 * resource-service's ResourceServiceApiIT turning up a real 500-instead-of-403
	 * regression the moment this handler was wired into a service that actually
	 * exercises @PreAuthorize.
	 */
	@Test
	void handleAccessDenied_rethrowsRatherThanHandlingIt() {
		AccessDeniedException ex = new AccessDeniedException("denied");

		assertThatThrownBy(() -> handler.handleAccessDenied(ex)).isSameAs(ex);
	}

	@Test
	void handleAuthenticationFailure_rethrowsRatherThanHandlingIt() {
		BadCredentialsException ex = new BadCredentialsException("bad credentials");

		assertThatThrownBy(() -> handler.handleAuthenticationFailure(ex)).isSameAs(ex);
	}

	@Test
	void handleMethodArgumentNotValid_returns400WithTheBindingResult() throws Exception {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "authRequest");
		bindingResult.rejectValue(null, "required", "username cannot be null");
		MethodParameter parameter = new MethodParameter(
				CustomizedResponseEntityExceptionHandlerTest.class.getDeclaredMethod("dummyTarget", String.class), 0);
		MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

		ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(ex, new HttpHeaders(),
				HttpStatus.BAD_REQUEST, mock(WebRequest.class));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		ExceptionResponse body = (ExceptionResponse) response.getBody();
		assertThat(body.getMessage()).isEqualTo("validation failed");
		assertThat(body.getDetails()).contains("username cannot be null");
	}

	/**
	 * @Valid on an implicit (unannotated) form-backed parameter - api-gateway's
	 * LoginService.login - fails with BindException rather than
	 * MethodArgumentNotValidException; both must produce the same response shape.
	 */
	@Test
	void handleBindException_returns400WithTheSameShapeAsMethodArgumentNotValid() {
		BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "authRequest");
		bindingResult.rejectValue(null, "required", "password must be at least 6 char long");
		BindException ex = new BindException(bindingResult);

		ResponseEntity<Object> response = handler.handleBindException(ex, new HttpHeaders(), HttpStatus.BAD_REQUEST,
				mock(WebRequest.class));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		ExceptionResponse body = (ExceptionResponse) response.getBody();
		assertThat(body.getMessage()).isEqualTo("validation failed");
		assertThat(body.getDetails()).contains("password must be at least 6 char long");
	}

	@SuppressWarnings("unused")
	private void dummyTarget(String arg) {
		// only exists to give MethodArgumentNotValidException's MethodParameter a real Method
	}
}
