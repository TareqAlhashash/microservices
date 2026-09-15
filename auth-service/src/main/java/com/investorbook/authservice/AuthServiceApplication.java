package com.investorbook.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.investorbook.common.exception.CustomizedResponseEntityExceptionHandler;

@SpringBootApplication
@Configuration
@EnableDiscoveryClient
@Import(CustomizedResponseEntityExceptionHandler.class)
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}
	
	
}
