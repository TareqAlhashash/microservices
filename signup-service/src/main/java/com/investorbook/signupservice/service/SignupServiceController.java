package com.investorbook.signupservice.service;

import java.util.Arrays;
import java.util.Optional;

import javax.validation.Valid;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.common.util.EncryptionUtil;
import com.investorbook.signupservice.bean.Member;
import com.investorbook.signupservice.dao.MemberRepository;
import com.investorbook.signupservice.dto.MemberDto;
import com.investorbook.signupservice.exception.MemberAlreadyExistsException;
import com.investorbook.signupservice.exception.MemberNotFoundException;

@RestController
public class SignupServiceController {

	@Autowired
	private MemberRepository memberRepository;

	
	
	@PostMapping("/signup")
	public ResponseEntity<MemberDto> signUpMember(@Valid @RequestBody MemberDto memberDto) {
		// member already exists
		if (memberRepository.existsById(memberDto.getEmail())) {
			throw new MemberAlreadyExistsException("email already exists");
		}
		
		ModelMapper modelMapper = new ModelMapper();

		Member member = modelMapper.map(memberDto, Member.class);
		
		member.setPasswordHash(EncryptionUtil.encode(EncryptionUtil.hash(memberDto.getPassword())));
		
		member = memberRepository.save(member);
		MemberDto result = modelMapper.map(member, MemberDto.class);
		return ResponseEntity.ok(result);
		
		
		//TODO call email service to send welcome message,this can be implemented using messaging to create the email asynchronously 
	}

	@PostMapping("/signin")
	public ResponseEntity<MemberDto> signInMember(@Valid @RequestBody MemberDto memberDto) {
		// user not found

		Optional<Member> existingMember = memberRepository.findById(memberDto.getEmail());
		if (!existingMember.isPresent()) {
			throw new MemberNotFoundException(memberDto.getEmail() + " is not found please sign up");
		}
		// hash the password to match with existing password
		byte[] password = EncryptionUtil.hashToMatch(memberDto.getPassword(),
				EncryptionUtil.decode(existingMember.get().getPasswordHash()));

		ModelMapper modelMapper = new ModelMapper();
		
		if (Arrays.equals(password, EncryptionUtil.decode(existingMember.get().getPasswordHash()))) {
			// TODO create token;
			return ResponseEntity.ok(modelMapper.map(existingMember.get(), MemberDto.class));
		}

		//not logged in
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(modelMapper.map(existingMember.get(), MemberDto.class));
	}
}
