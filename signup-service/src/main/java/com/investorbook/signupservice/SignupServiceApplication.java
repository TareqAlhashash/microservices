package com.investorbook.signupservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import brave.sampler.Sampler;

/**
 * enables eureka client to register this service in the naming server, using
 * (from properties file) 'spring.application.name' value
 * 'currency-conversion-service' as the name
 */
@EnableDiscoveryClient
@SpringBootApplication
@EnableFeignClients("com.investorbook")
public class SignupServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SignupServiceApplication.class, args);
	}
	
	@Bean
	public Sampler defaultSampler(){
		return Sampler.ALWAYS_SAMPLE;
	}
}
