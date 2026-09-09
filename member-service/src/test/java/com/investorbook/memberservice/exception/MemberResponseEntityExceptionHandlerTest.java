package com.investorbook.memberservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class MemberResponseEntityExceptionHandlerTest {

	private final MemberResponseEntityExceptionHandler handler = new MemberResponseEntityExceptionHandler();
	private final WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

	@Test
	void handleMemberNotFoundException_returns404() {
		ResponseEntity<Object> response = handler
				.handleMemberNotFoundException(new MemberNotFoundException("nobody@example.com not found"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void handleMemberAlreadyExistsException_returns409Conflict_notARedirect() {
		ResponseEntity<Object> response = handler
				.handleMemberAlreadyExistsException(new MemberAlreadyExistsException("email already exists"), request);

		// a duplicate-email signup is a conflict, not something a client should
		// follow a redirect for
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void handleMemberUploadPicException_returns417() {
		ResponseEntity<Object> response = handler
				.handleMemberUploadPicException(new MemberUploadPicException("could not upload pic"), request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.EXPECTATION_FAILED);
	}
}
