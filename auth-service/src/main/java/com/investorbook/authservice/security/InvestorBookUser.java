package com.investorbook.authservice.security;

import org.springframework.security.core.userdetails.User;

import com.investorbook.authservice.dao.entities.MemberEntity;

public class InvestorBookUser extends User {

	private static final long serialVersionUID = 1L;

	public InvestorBookUser(MemberEntity member) {
		// TODO add user type ex. premium/normal in member
		super(member.getEmail(), member.getPasswordHash(), GrantedAuthorities.NORMAL_USER.grantedAuthorities());
	}
}
