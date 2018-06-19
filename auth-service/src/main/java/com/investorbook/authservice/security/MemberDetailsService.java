package com.investorbook.authservice.security;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.investorbook.authservice.dao.MemberRepository;


@Service
public class MemberDetailsService implements UserDetailsService {

	@Autowired
	private MemberRepository memberRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

		return memberRepository.findOptionalByEmail(username)
				.map(memberEntity -> new InvestorBookUser(memberEntity))
				.orElseThrow(() -> new UsernameNotFoundException("couldn't find user:" + username));

	}

}
