package com.investorbook.authservice.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

public enum GrantedAuthorities {

	NORMAL_USER, PREMIUM_USER, ADMIN;

	List<GrantedAuthority> grantedAuthorities() {
		switch (this) {
		case NORMAL_USER:
			return AuthorityUtils.createAuthorityList("ROLE_MEMBER");
		case PREMIUM_USER:
			return AuthorityUtils.createAuthorityList("ROLE_MEMBER", "ROLE_PREMIUMMEMBER");
		case ADMIN:
			return AuthorityUtils.createAuthorityList("ROLE_MEMBER", "ROLE_PREMIUMMEMBER", "ROLE_ADMIN");
		default:
			return AuthorityUtils.createAuthorityList("");
		}
	}
}
