package com.investorbook.resourceservice.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint in this template service - see ResourceServiceApplication's Javadoc: this
 * whole module exists as a copy-paste starting point for a new @EnableResourceServer service,
 * not a real feature.
 */
@RestController
public class HelloController {

	@GetMapping("/hi")
	@PreAuthorize("hasRole('MEMBER')")
	public String hi() {
		return "hi";
	}
}
