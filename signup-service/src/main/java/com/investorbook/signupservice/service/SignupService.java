package com.investorbook.signupservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.signupservice.proxy.TestServiceProxy;

@RestController
public class SignupService {

	@Autowired
	private TestServiceProxy proxy;

	@GetMapping("/signup")
	public String retrieveValue() {

		// call the test service
		return proxy.retrieveTestServiceValue();
	}
}
