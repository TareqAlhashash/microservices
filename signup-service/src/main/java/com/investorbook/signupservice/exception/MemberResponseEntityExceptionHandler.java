package com.investorbook.signupservice.exception;

import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.investorbook.common.exception.CustomizedResponseEntityExceptionHandler;
import com.investorbook.common.exception.ExceptionResponse;

@ControllerAdvice
@RestController
public class SignupResponseEntityExceptionHandler extends CustomizedResponseEntityExceptionHandler{

	@ExceptionHandler(MemberNotFoundException.class)
	public final ResponseEntity<Object> handleMemberNotFoundException(Exception ex, WebRequest request) {
		ExceptionResponse exceptionResponse = new ExceptionResponse(new Date(), ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(exceptionResponse, HttpStatus.NOT_FOUND);
	}
	
	@ExceptionHandler(MemberAlreadyExistsException.class)
	public final ResponseEntity<Object> handleMemberAlreadyExistsException(Exception ex, WebRequest request) {
		ExceptionResponse exceptionResponse = new ExceptionResponse(new Date(), ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(exceptionResponse, HttpStatus.FOUND);
	}
}
