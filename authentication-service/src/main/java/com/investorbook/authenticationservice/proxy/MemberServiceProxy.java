package com.investorbook.authenticationservice.proxy;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;



@FeignClient(name = "api-gateway")
public interface MemberServiceProxy {

	@GetMapping("/member-service/member/password/{email}")
	public byte[] getMember(@PathVariable("email") String email);

}
