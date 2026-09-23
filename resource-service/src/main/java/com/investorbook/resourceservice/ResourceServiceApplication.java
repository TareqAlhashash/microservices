package com.investorbook.resourceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;

import com.investorbook.common.exception.CustomizedResponseEntityExceptionHandler;

/**
 * Skeletal @EnableResourceServer example service (see controller.HelloController's single /hi
 * endpoint) - a template for adding new protected microservices, not a real feature.
 */
@SpringBootApplication
@EnableResourceServer
@EnableDiscoveryClient
@Import(CustomizedResponseEntityExceptionHandler.class)
public class ResourceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResourceServiceApplication.class, args);
	}
}
