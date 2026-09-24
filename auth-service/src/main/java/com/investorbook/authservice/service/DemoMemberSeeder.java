package com.investorbook.authservice.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.investorbook.authservice.dao.MemberRepository;
import com.investorbook.authservice.dao.entities.MemberEntity;

/**
 * Seeds one demo member on first startup only (skipped once a row with this email already
 * exists), so restarting the service doesn't keep re-inserting it. This is the only account
 * provisioning left in the system since member-service (which owned /signup) was removed as
 * out of scope for the purchase-order flow - matches ProductCatalogSeeder's fixed, deterministic
 * approach for the same reason: a login demo that stays stable across restarts and tests.
 */
@Component
public class DemoMemberSeeder implements ApplicationRunner {

	private static final String DEMO_ID = "demo-member";
	private static final String DEMO_EMAIL = "demo@investorbook.com";
	private static final String DEMO_PASSWORD = "demo12345";

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	public DemoMemberSeeder(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (memberRepository.findOptionalByEmail(DEMO_EMAIL).isEmpty()) {
			memberRepository.save(new MemberEntity(DEMO_ID, DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD)));
		}
	}
}
