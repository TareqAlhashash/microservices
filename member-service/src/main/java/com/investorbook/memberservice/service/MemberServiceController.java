package com.investorbook.memberservice.service;

import java.util.Optional;

import javax.validation.Valid;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.common.util.EncryptionUtil;
import com.investorbook.memberservice.dao.MemberRepository;
import com.investorbook.memberservice.dao.entites.MemberEntity;
import com.investorbook.memberservice.dto.Member;
import com.investorbook.memberservice.exception.MemberAlreadyExistsException;
import com.investorbook.memberservice.exception.MemberNotFoundException;


@RestController
public class MemberServiceController {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	
	@Autowired
	private MemberRepository memberRepository;

	
	
	@PostMapping("/signup")
	public ResponseEntity<Member> signUpMember(@Valid @RequestBody Member memberDto) {
		
		logger.info("{}",memberDto.getEmail());
		
		if (memberRepository.findOptionalByEmail(memberDto.getEmail()).isPresent()) {
			throw new MemberAlreadyExistsException("email already exists");
		}
		
		ModelMapper modelMapper = new ModelMapper();

		MemberEntity member = modelMapper.map(memberDto, MemberEntity.class);
		
		member.setPasswordHash(EncryptionUtil.encode(EncryptionUtil.hash(memberDto.getPassword())));
		
		member = memberRepository.save(member);
		Member result = modelMapper.map(member, Member.class);
		return ResponseEntity.ok(result);
		
		
		//TODO call email service to send welcome message,this can be implemented using messaging to create the email asynchronously 
	}

	@GetMapping("/member/{email}")
	public ResponseEntity<Member> getMember(@PathVariable String email) {
		Optional<MemberEntity> existingMember = memberRepository.findOptionalByEmail(email);
		if (!existingMember.isPresent()) {
			throw new MemberNotFoundException(email + " is not found please sign up");
		}
		ModelMapper modelMapper = new ModelMapper();
		return ResponseEntity.ok(modelMapper.map(existingMember.get(), Member.class));
	}
	
}
