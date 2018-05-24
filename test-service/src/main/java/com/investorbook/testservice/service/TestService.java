package com.investorbook.testservice.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class TestService {
	@Autowired
	private Environment environment;

	@GetMapping("/hi")
	public String retrieveSampleValue() {
		return "hi - this has been processed by test service on port:" + environment.getProperty("local.server.port");
	}
}
