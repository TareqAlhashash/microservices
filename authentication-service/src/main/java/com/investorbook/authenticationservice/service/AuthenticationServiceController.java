package com.investorbook.authenticationservice.service;

import java.util.Arrays;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.authenticationservice.dto.Member;
import com.investorbook.authenticationservice.exception.AuthenticationException;
import com.investorbook.authenticationservice.proxy.MemberServiceProxy;
import com.investorbook.common.util.EncryptionUtil;

@RestController
public class AuthenticationServiceController {

	@Autowired
	MemberServiceProxy proxy;

	@PostMapping("/signin")
	public ResponseEntity<String> signInMember(@Valid @RequestBody Member memberDto) {
		byte[] passwordHash;
		try {
			passwordHash = proxy.getMember(memberDto.getEmail());
		} catch (RuntimeException e) {
			throw new AuthenticationException("auth failed for: " + memberDto.getEmail());
		}

		// hash the password to match with the existing password
		byte[] password = EncryptionUtil.hashToMatch(memberDto.getPassword(), EncryptionUtil.decode(passwordHash));

		if (Arrays.equals(password, EncryptionUtil.decode(passwordHash))) {
			// TODO create token;
			return ResponseEntity.accepted().build();
		}

		// not logged in
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body("not authenticated:" + memberDto.getEmail());
	}
}
