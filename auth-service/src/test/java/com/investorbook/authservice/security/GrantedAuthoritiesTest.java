package com.investorbook.authservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class GrantedAuthoritiesTest {

	@Test
	void normalUser_getsOnlyMemberRole() {
		assertThat(roleNames(GrantedAuthorities.NORMAL_USER)).containsExactly("ROLE_MEMBER");
	}

	@Test
	void premiumUser_getsMemberAndPremiumRoles() {
		assertThat(roleNames(GrantedAuthorities.PREMIUM_USER)).containsExactlyInAnyOrder("ROLE_MEMBER",
				"ROLE_PREMIUMMEMBER");
	}

	@Test
	void admin_getsMemberPremiumAndAdminRoles() {
		assertThat(roleNames(GrantedAuthorities.ADMIN)).containsExactlyInAnyOrder("ROLE_MEMBER",
				"ROLE_PREMIUMMEMBER", "ROLE_ADMIN");
	}

	private static List<String> roleNames(GrantedAuthorities authorities) {
		return authorities.grantedAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toList());
	}
}
