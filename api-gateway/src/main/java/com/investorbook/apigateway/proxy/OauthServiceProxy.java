package com.investorbook.apigateway.proxy;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.investorbook.apigateway.config.CoreFeignConfiguration;
import com.investorbook.apigateway.dto.AuthResponse;

import feign.Headers;

@FeignClient(name = "auth-service", configuration = CoreFeignConfiguration.class)
public interface OauthServiceProxy {

	@PostMapping(path = "/uaa/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@Headers("Content-Type: " + MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public AuthResponse login(@RequestHeader("Authorization") String auth, Map<String, ?> formParams);

}
