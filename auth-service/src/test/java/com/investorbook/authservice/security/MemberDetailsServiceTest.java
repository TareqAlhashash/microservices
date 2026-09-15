package com.investorbook.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.investorbook.authservice.dao.MemberRepository;
import com.investorbook.authservice.dao.entities.MemberEntity;

@ExtendWith(MockitoExtension.class)
class MemberDetailsServiceTest {

	@Mock
	private MemberRepository memberRepository;

	private MemberDetailsService memberDetailsService;

	@BeforeEach
	void setUp() {
		memberDetailsService = new MemberDetailsService(memberRepository);
	}

	@Test
	void loadUserByUsername_returnsAUserWithTheStoredHashAndMemberRole_whenTheMemberExists() {
		MemberEntity member = new MemberEntity("jane@example.com", "bcrypt-hash");
		when(memberRepository.findOptionalByEmail("jane@example.com")).thenReturn(Optional.of(member));

		UserDetails user = memberDetailsService.loadUserByUsername("jane@example.com");

		assertThat(user.getUsername()).isEqualTo("jane@example.com");
		assertThat(user.getPassword()).isEqualTo("bcrypt-hash");
		assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MEMBER");
	}

	@Test
	void loadUserByUsername_throwsUsernameNotFoundException_whenNoMemberMatchesTheEmail() {
		when(memberRepository.findOptionalByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> memberDetailsService.loadUserByUsername("nobody@example.com"))
				.isInstanceOf(UsernameNotFoundException.class).hasMessageContaining("nobody@example.com");
	}
}
