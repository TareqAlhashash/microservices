package com.investorbook.memberservice.proxy;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;

import com.investorbook.common.dto.AuthResponse;
import com.investorbook.common.util.CoreFeignConfiguration;

import feign.Headers;

@FeignClient(name = "api-gateway", configuration = CoreFeignConfiguration.class) // api gatewave server
public interface AuthenticationServiceProxy {

	@PostMapping(path = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	@Headers("Content-Type: " + MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public ResponseEntity<AuthResponse> login(Map<String, ?> formParams);

}
