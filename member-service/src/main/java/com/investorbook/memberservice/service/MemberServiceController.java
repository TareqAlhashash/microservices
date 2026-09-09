package com.investorbook.memberservice.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.validation.Valid;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.investorbook.common.aws.S3Utils;
import com.investorbook.common.dto.AuthRequest;
import com.investorbook.common.dto.AuthResponse;
import com.investorbook.common.util.JwtUtil;
import com.investorbook.memberservice.dao.MemberRepository;
import com.investorbook.memberservice.dao.entites.MemberEntity;
import com.investorbook.memberservice.dto.Member;
import com.investorbook.memberservice.exception.MemberAlreadyExistsException;
import com.investorbook.memberservice.exception.MemberNotFoundException;
import com.investorbook.memberservice.exception.MemberUploadPicException;
import com.investorbook.memberservice.proxy.AuthenticationServiceProxy;

@RestController
public class MemberServiceController {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private AuthenticationServiceProxy authenticationServiceProxy;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signUpMember(@Valid @RequestBody Member memberDto) {

		logger.info("{}", memberDto.getEmail());

		if (memberRepository.findOptionalByEmail(memberDto.getEmail()).isPresent()) {
			throw new MemberAlreadyExistsException("email already exists");
		}

		ModelMapper modelMapper = new ModelMapper();

		MemberEntity member = modelMapper.map(memberDto, MemberEntity.class);

		member.setPasswordHash(passwordEncoder().encode(memberDto.getPassword()));

		member = memberRepository.save(member);

		// login and create a token and send it back
		ResponseEntity<AuthResponse> authREsponse = authenticationServiceProxy
				.login(new AuthRequest(memberDto.getEmail(), memberDto.getPassword()).toFormParams());
		return authREsponse;

		// TODO call email service to send welcome message and a verify link,this can be
		// implemented using messaging to create the email asynchronously
	}

	/**
	 * retrieves the member
	 * 
	 * @param email
	 * @return
	 */
	@GetMapping("/member")
	@PreAuthorize("hasRole('MEMBER')")
	public ResponseEntity<Member> getMember() {
		String email = JwtUtil.getEmail(SecurityContextHolder.getContext().getAuthentication()).get();
		Optional<MemberEntity> existingMember = memberRepository.findOptionalByEmail(email);
		if (!existingMember.isPresent()) {
			throw new MemberNotFoundException(email + " is not found please sign up");
		}
		ModelMapper modelMapper = new ModelMapper();
		return ResponseEntity.ok(modelMapper.map(existingMember.get(), Member.class));
	}

	/**
	 * updates the member, requires the email of the user to fetch him
	 * 
	 * @param memberDto
	 * @return
	 */
	@PostMapping("/member")
	@PreAuthorize("hasRole('MEMBER')")
	public ResponseEntity<Member> updateMember(@Valid @RequestBody Member memberDto) {
		String email = JwtUtil.getEmail(SecurityContextHolder.getContext().getAuthentication()).get();
		// ignore any email sent, we only use the token email
		memberDto.setEmail(email);

		// retrieve the member
		Optional<MemberEntity> existingMember = memberRepository.findOptionalByEmail(email);
		if (!existingMember.isPresent()) {
			throw new MemberNotFoundException(email + " is not found please sign up");
		}

		ModelMapper modelMapper = new ModelMapper();
		modelMapper.map(memberDto, existingMember.get());

		// set the reference for sub entities
		existingMember.get().getAddress().setMember(existingMember.get());

		memberRepository.save(existingMember.get());
		return ResponseEntity.ok(memberDto);
	}

	@PostMapping("/member/pic")
	@PreAuthorize("hasRole('MEMBER')")
	public String uploadMemberPic(@RequestPart(value = "file") MultipartFile file) {
		String email = JwtUtil.getEmail(SecurityContextHolder.getContext().getAuthentication()).get();
		MemberEntity member = memberRepository.findOptionalByEmail(email).get();
		String url = "";
		try {
			url = S3Utils.uploadProfilePic(file, member.getId());
			member.setPhotoUrl(url);
			memberRepository.save(member);
		} catch (IOException e1) {
			throw new MemberUploadPicException("could not upload pic", e1);
		}

		return S3Utils.getPresignedUrl(url).toString();

	}

	@GetMapping("/member/pic")
	@PreAuthorize("hasRole('MEMBER')")
	public String getMemberPic() {
		String email = JwtUtil.getEmail(SecurityContextHolder.getContext().getAuthentication()).get();
		URL url = S3Utils.getPresignedUrl(memberRepository.findOptionalByEmail(email).get().getPhotoUrl());
		return url.toString();

	}

}
