package com.investorbook.memberservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import com.investorbook.common.aws.ProfilePictureStorage;
import com.investorbook.common.dto.AuthResponse;
import com.investorbook.memberservice.dao.MemberRepository;
import com.investorbook.memberservice.dao.entites.AddressEntity;
import com.investorbook.memberservice.dao.entites.MemberEntity;
import com.investorbook.memberservice.dto.Address;
import com.investorbook.memberservice.dto.Member;
import com.investorbook.memberservice.exception.MemberAlreadyExistsException;
import com.investorbook.memberservice.exception.MemberNotFoundException;
import com.investorbook.memberservice.exception.MemberUploadPicException;
import com.investorbook.memberservice.proxy.AuthenticationServiceProxy;

@ExtendWith(MockitoExtension.class)
class MemberServiceControllerTest {

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private AuthenticationServiceProxy authenticationServiceProxy;

	@Mock
	private ProfilePictureStorage profilePictureStorage;

	@Mock
	private PasswordEncoder passwordEncoder;

	private MemberServiceController controller;

	@BeforeEach
	void createController() {
		controller = new MemberServiceController(memberRepository, authenticationServiceProxy,
				profilePictureStorage, passwordEncoder);
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	private static void authenticateAs(String email) {
		OAuth2AuthenticationDetails details = new OAuth2AuthenticationDetails(new MockHttpServletRequest());
		details.setDecodedDetails(Collections.singletonMap("user_name", email));
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email, null,
				Collections.emptyList());
		authentication.setDetails(details);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private static MemberEntity existingMember(String email) {
		AddressEntity address = new AddressEntity("addr-1", "1 Test St", null, "Testville", "QLD", "4000", "AU",
				null);
		MemberEntity member = new MemberEntity(email, "Jane", "Doe", "about me", address, "hashed-pw", null);
		ReflectionTestUtils.setField(member, "id", "member-1");
		return member;
	}

	@Test
	void signUpMember_savesHashedPasswordAndReturnsTokenFromAuthProxy() {
		Member request = new Member("new@example.com", "Jane", "Doe", "plaintext1", "about", new Address());
		when(memberRepository.findOptionalByEmail("new@example.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("plaintext1")).thenReturn("hashed-pw");
		AuthResponse token = new AuthResponse("access", "refresh", "bearer", "3600", "read", "jti-1");
		when(authenticationServiceProxy.login(any())).thenReturn(ResponseEntity.ok(token));

		ResponseEntity<AuthResponse> response = controller.signUpMember(request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isEqualTo(token);

		ArgumentCaptor<MemberEntity> saved = ArgumentCaptor.forClass(MemberEntity.class);
		verify(memberRepository).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed-pw");
	}

	@Test
	void signUpMember_rejectsDuplicateEmail_withoutSavingOrLoggingIn() {
		Member request = new Member("taken@example.com", "Jane", "Doe", "plaintext1", "about", new Address());
		when(memberRepository.findOptionalByEmail("taken@example.com"))
				.thenReturn(Optional.of(existingMember("taken@example.com")));

		assertThatThrownBy(() -> controller.signUpMember(request)).isInstanceOf(MemberAlreadyExistsException.class);

		verify(memberRepository, never()).save(any());
		verify(authenticationServiceProxy, never()).login(any());
	}

	@Test
	void getMember_returnsMappedMember_whenFound() {
		authenticateAs("jane@example.com");
		when(memberRepository.findOptionalByEmail("jane@example.com"))
				.thenReturn(Optional.of(existingMember("jane@example.com")));

		ResponseEntity<Member> response = controller.getMember();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().getFirstName()).isEqualTo("Jane");
		assertThat(response.getBody().getLastName()).isEqualTo("Doe");
	}

	@Test
	void getMember_throwsNotFound_whenNoMemberForAuthenticatedEmail() {
		authenticateAs("missing@example.com");
		when(memberRepository.findOptionalByEmail("missing@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> controller.getMember()).isInstanceOf(MemberNotFoundException.class);
	}

	@Test
	void updateMember_ignoresRequestEmail_usesAuthenticatedEmailAndRelinksAddress() {
		authenticateAs("jane@example.com");
		MemberEntity existing = existingMember("jane@example.com");
		when(memberRepository.findOptionalByEmail("jane@example.com")).thenReturn(Optional.of(existing));

		Member request = new Member("attacker@example.com", "Janet", "Doe", null, "updated about", new Address());

		ResponseEntity<Member> response = controller.updateMember(request);

		assertThat(response.getBody().getEmail()).isEqualTo("jane@example.com");

		ArgumentCaptor<MemberEntity> saved = ArgumentCaptor.forClass(MemberEntity.class);
		verify(memberRepository).save(saved.capture());
		assertThat(saved.getValue().getFirstName()).isEqualTo("Janet");
		assertThat(saved.getValue().getAddress().getMember()).isSameAs(existing);
	}

	@Test
	void updateMember_throwsNotFound_whenNoMemberForAuthenticatedEmail() {
		authenticateAs("missing@example.com");
		when(memberRepository.findOptionalByEmail("missing@example.com")).thenReturn(Optional.empty());
		Member request = new Member(null, "Janet", "Doe", null, "updated about", new Address());

		assertThatThrownBy(() -> controller.updateMember(request)).isInstanceOf(MemberNotFoundException.class);
	}

	@Test
	void uploadMemberPic_storesReturnedKeyAndRespondsWithPresignedUrl() throws IOException {
		authenticateAs("jane@example.com");
		MemberEntity existing = existingMember("jane@example.com");
		when(memberRepository.findOptionalByEmail("jane@example.com")).thenReturn(Optional.of(existing));
		MultipartFile file = new MockMultipartFile("file", "pic.png", "image/png", "content".getBytes());
		when(profilePictureStorage.upload(eq(file), eq("member-1"))).thenReturn("member-1.png");
		when(profilePictureStorage.getPresignedUrl("member-1.png")).thenReturn(url("https://s3.example.com/signed"));

		String result = controller.uploadMemberPic(file);

		assertThat(result).isEqualTo("https://s3.example.com/signed");
		ArgumentCaptor<MemberEntity> saved = ArgumentCaptor.forClass(MemberEntity.class);
		verify(memberRepository).save(saved.capture());
		assertThat(saved.getValue().getPhotoUrl()).isEqualTo("member-1.png");
	}

	@Test
	void uploadMemberPic_wrapsStorageIOException_asMemberUploadPicException() throws IOException {
		authenticateAs("jane@example.com");
		when(memberRepository.findOptionalByEmail("jane@example.com"))
				.thenReturn(Optional.of(existingMember("jane@example.com")));
		MultipartFile file = new MockMultipartFile("file", "pic.png", "image/png", "content".getBytes());
		when(profilePictureStorage.upload(any(), any())).thenThrow(new IOException("s3 unavailable"));

		assertThatThrownBy(() -> controller.uploadMemberPic(file)).isInstanceOf(MemberUploadPicException.class);

		verify(memberRepository, never()).save(any());
	}

	@Test
	void getMemberPic_returnsPresignedUrlForStoredKey() {
		authenticateAs("jane@example.com");
		MemberEntity existing = existingMember("jane@example.com");
		ReflectionTestUtils.setField(existing, "photoUrl", "member-1.png");
		when(memberRepository.findOptionalByEmail("jane@example.com")).thenReturn(Optional.of(existing));
		when(profilePictureStorage.getPresignedUrl("member-1.png")).thenReturn(url("https://s3.example.com/signed"));

		String result = controller.getMemberPic();

		assertThat(result).isEqualTo("https://s3.example.com/signed");
	}

	private static URL url(String value) {
		try {
			return new URL(value);
		} catch (MalformedURLException e) {
			throw new AssertionError(e);
		}
	}
}
