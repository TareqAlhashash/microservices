package com.investorbook.memberservice.service;

import java.util.Optional;

import javax.validation.Valid;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
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
import com.investorbook.memberservice.dao.entites.AddressEntity;
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

	/**
	 * retrieves the member
	 * @param email
	 * @return
	 */
	@GetMapping("/member/{email}")
	public ResponseEntity<Member> getMember(@PathVariable String email) {
		Optional<MemberEntity> existingMember = memberRepository.findOptionalByEmail(email);
		if (!existingMember.isPresent()) {
			throw new MemberNotFoundException(email + " is not found please sign up");
		}
		ModelMapper modelMapper = new ModelMapper();
		return ResponseEntity.ok(modelMapper.map(existingMember.get(), Member.class));
	}
	
	/**
	 * updates the member, requires the email of the user to fetch him
	 * @param memberDto
	 * @return
	 */
	@PostMapping("/member")
	public ResponseEntity<Member> updateMember(@Valid @RequestBody Member memberDto) {
		Optional<MemberEntity> existingMember = memberRepository.findOptionalByEmail(memberDto.getEmail());
		if (!existingMember.isPresent()) {
			throw new MemberNotFoundException(memberDto.getEmail() + " is not found please sign up");
		}
		ModelMapper modelMapper = new ModelMapper();
		modelMapper.map(memberDto, existingMember.get());
		
		//TODO razi? do i have to to this step manually, setting the member in address?
		existingMember.get().getAddress().setMember(existingMember.get());


		memberRepository.save(existingMember.get());
		return ResponseEntity.ok(memberDto);
	}
	
}
