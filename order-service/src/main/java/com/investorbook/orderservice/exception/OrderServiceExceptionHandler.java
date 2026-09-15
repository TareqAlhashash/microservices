package com.investorbook.orderservice.exception;

import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import com.investorbook.common.exception.CustomizedResponseEntityExceptionHandler;
import com.investorbook.common.exception.ExceptionResponse;
import com.investorbook.orderservice.service.OrderNotFoundException;

@ControllerAdvice
@RestController
public class OrderServiceExceptionHandler extends CustomizedResponseEntityExceptionHandler {

	@ExceptionHandler(OrderNotFoundException.class)
	public final ResponseEntity<Object> handleOrderNotFoundException(Exception ex, WebRequest request) {
		ExceptionResponse exceptionResponse = new ExceptionResponse(new Date(), ex.getMessage(),
				request.getDescription(false));
		return new ResponseEntity<>(exceptionResponse, HttpStatus.NOT_FOUND);
	}
}
