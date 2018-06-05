package com.investorbook.authenticationservice.service;

import java.util.Arrays;
import java.util.Optional;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.authenticationservice.dao.AuthenticationRepository;
import com.investorbook.authenticationservice.dao.entities.AuthenticationEntity;
import com.investorbook.authenticationservice.dto.Member;
import com.investorbook.authenticationservice.exception.AuthenticationException;
import com.investorbook.common.util.EncryptionUtil;

@RestController
public class AuthenticationServiceController {

	@Autowired
	AuthenticationRepository repository;

	@PostMapping("/signin")
	public ResponseEntity<String> signInMember(@Valid @RequestBody Member memberDto) {
		
			Optional<AuthenticationEntity> member = repository.findOptionalByEmail(memberDto.getEmail());
			if(!member.isPresent()) {
				throw new AuthenticationException("auth failed for: " + memberDto.getEmail() + " member not found");		
			}
		
		// hash the password to match with the existing password
		byte[] password = EncryptionUtil.hashToMatch(memberDto.getPassword(), EncryptionUtil.decode(member.get().getPasswordHash()));

		if (Arrays.equals(password, EncryptionUtil.decode(member.get().getPasswordHash()))) {
			// TODO create token;
			return ResponseEntity.accepted().build();
		}

		// not logged in
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body("not authenticated:" + memberDto.getEmail());
	}
}
