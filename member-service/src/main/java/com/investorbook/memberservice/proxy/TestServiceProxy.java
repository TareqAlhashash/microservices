package com.investorbook.memberservice.proxy;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "api-gateway") // api gatewave server
public interface TestServiceProxy {

	@GetMapping("/test-service/hi")
	public String retrieveTestServiceValue();

}
