package com.investorbook.memberservice.service;

import java.io.IOException;
import java.util.Optional;

import javax.validation.Valid;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.investorbook.common.aws.ProfilePictureStorage;
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

	private static final Logger logger = LoggerFactory.getLogger(MemberServiceController.class);

	private final MemberRepository memberRepository;
	private final AuthenticationServiceProxy authenticationServiceProxy;
	private final ProfilePictureStorage profilePictureStorage;
	private final PasswordEncoder passwordEncoder;

	public MemberServiceController(MemberRepository memberRepository,
			AuthenticationServiceProxy authenticationServiceProxy, ProfilePictureStorage profilePictureStorage,
			PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.authenticationServiceProxy = authenticationServiceProxy;
		this.profilePictureStorage = profilePictureStorage;
		this.passwordEncoder = passwordEncoder;
	}

	@PostMapping("/signup")
	public ResponseEntity<AuthResponse> signUpMember(@Valid @RequestBody Member memberDto) {

		logger.info("signup requested for {}", sanitizeForLog(memberDto.getEmail()));

		if (memberRepository.findOptionalByEmail(memberDto.getEmail()).isPresent()) {
			throw new MemberAlreadyExistsException("email already exists");
		}

		ModelMapper modelMapper = new ModelMapper();

		MemberEntity member = modelMapper.map(memberDto, MemberEntity.class);

		member.setPasswordHash(passwordEncoder.encode(memberDto.getPassword()));

		// AddressEntity shares its primary key with the member via @MapsId, which
		// Hibernate derives from this back-reference - without it, saving a member
		// with an address fails outright (IdentifierGenerationException).
		if (member.getAddress() != null) {
			member.getAddress().setMember(member);
		}

		memberRepository.save(member);

		// login and create a token and send it back
		return authenticationServiceProxy
				.login(new AuthRequest(memberDto.getEmail(), memberDto.getPassword()).toFormParams());

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
		MemberEntity existingMember = findMemberByEmailOrThrow(currentEmail());
		ModelMapper modelMapper = new ModelMapper();
		return ResponseEntity.ok(modelMapper.map(existingMember, Member.class));
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
		String email = currentEmail();
		// ignore any email sent, we only use the token email
		memberDto.setEmail(email);

		MemberEntity existingMember = findMemberByEmailOrThrow(email);

		ModelMapper modelMapper = new ModelMapper();
		modelMapper.map(memberDto, existingMember);

		// set the reference for sub entities
		existingMember.getAddress().setMember(existingMember);

		memberRepository.save(existingMember);
		return ResponseEntity.ok(memberDto);
	}

	@PostMapping("/member/pic")
	@PreAuthorize("hasRole('MEMBER')")
	public String uploadMemberPic(@RequestPart(value = "file") MultipartFile file) {
		MemberEntity member = findMemberByEmailOrThrow(currentEmail());
		try {
			String key = profilePictureStorage.upload(file, member.getId());
			member.setPhotoUrl(key);
			memberRepository.save(member);
			return profilePictureStorage.getPresignedUrl(key).toString();
		} catch (IOException e) {
			throw new MemberUploadPicException("could not upload pic", e);
		}
	}

	@GetMapping("/member/pic")
	@PreAuthorize("hasRole('MEMBER')")
	public String getMemberPic() {
		MemberEntity member = findMemberByEmailOrThrow(currentEmail());
		return profilePictureStorage.getPresignedUrl(member.getPhotoUrl()).toString();
	}

	private MemberEntity findMemberByEmailOrThrow(String email) {
		Optional<MemberEntity> member = memberRepository.findOptionalByEmail(email);
		return member.orElseThrow(() -> new MemberNotFoundException(email + " is not found please sign up"));
	}

	// Strips CR/LF so untrusted input (e.g. an email a caller controls) can't forge
	// extra log lines or corrupt log-file structure (CRLF/log injection).
	private static String sanitizeForLog(String value) {
		return value == null ? null : value.replaceAll("[\r\n]", "_");
	}

	private static String currentEmail() {
		return JwtUtil.getEmail(SecurityContextHolder.getContext().getAuthentication())
				.orElseThrow(() -> new IllegalStateException("authenticated request missing user_name claim"));
	}
}
