package com.investorbook.signupservice.service;

import java.util.Arrays;
import java.util.Optional;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.common.util.EncryptionUtil;
import com.investorbook.signupservice.bean.Member;
import com.investorbook.signupservice.dao.MemberRepository;
import com.investorbook.signupservice.exception.MemberAlreadyExistsException;
import com.investorbook.signupservice.exception.MemberNotFoundException;
import com.investorbook.signupservice.proxy.TestServiceProxy;

@RestController
public class SignupService {

	@Autowired
	private TestServiceProxy proxy;

	@Autowired
	private MemberRepository memberRepository;

	@GetMapping("/signup")
	public String retrieveValue() {

		// call the test service
		return proxy.retrieveTestServiceValue();
	}

	@PostMapping("/signup")
	public ResponseEntity<Member> signUpMember(@Valid @RequestBody Member member) {
		// member already exists
		if (memberRepository.existsById(member.getEmail())) {
			throw new MemberAlreadyExistsException("email already exists");
		}

		member.setPasswordHash(EncryptionUtil.encode(EncryptionUtil.hash(member.getPassword())));
		
		//TODO setup smtp to send welcome emails
		return ResponseEntity.ok(memberRepository.save(member));
	}

	@PostMapping("/signin")
	public ResponseEntity<Member> signInMember(@Valid @RequestBody Member member) {
		// user not found

		Optional<Member> existingMember = memberRepository.findById(member.getEmail());
		if (!existingMember.isPresent()) {
			throw new MemberNotFoundException(member.getEmail() + " is not found please sign up");
		}
		// hash the password to match with existing password
		byte[] password = EncryptionUtil.hashToMatch(member.getPassword(),
				EncryptionUtil.decode(existingMember.get().getPasswordHash()));

		if (Arrays.equals(password, EncryptionUtil.decode(existingMember.get().getPasswordHash()))) {
			// TODO create token;
			return ResponseEntity.ok(existingMember.get());
		}

		//not logged in
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(existingMember.get());
	}
}
