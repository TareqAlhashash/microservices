package com.investorbook.memberservice.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.investorbook.memberservice.dao.entites.AddressEntity;
import com.investorbook.memberservice.dao.entites.MemberEntity;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @DataJpaTest otherwise bootstraps from MemberServiceApplication, the nearest
// @SpringBootConfiguration - pulling in @EnableResourceServer and failing for
// missing security beans this slice has no business needing. A minimal,
// dao-package-scoped bootstrap class sidesteps that entirely.
@ContextConfiguration(classes = MemberPersistenceIT.TestConfig.class)
class MemberPersistenceIT {

	@SpringBootApplication
	static class TestConfig {
	}

	@Container
	private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TestEntityManager entityManager;

	private static MemberEntity newMember(String email) {
		return new MemberEntity(email, "Jane", "Doe", "about me", null, "hashed-pw", null);
	}

	@Test
	void save_generatesAnId_andCascadesTheLinkedAddress() {
		MemberEntity member = newMember("jane@example.com");
		AddressEntity address = new AddressEntity(null, "1 Test St", null, "Testville", "QLD", "4000", "AU", member);
		member.setAddress(address);

		memberRepository.save(member);
		entityManager.flush();
		entityManager.clear();

		Optional<MemberEntity> reloaded = memberRepository.findOptionalByEmail("jane@example.com");
		assertThat(reloaded).isPresent();
		assertThat(reloaded.get().getId()).isNotBlank();
		assertThat(reloaded.get().getAddress()).isNotNull();
		assertThat(reloaded.get().getAddress().getLine1()).isEqualTo("1 Test St");
		assertThat(reloaded.get().getAddress().getId()).isEqualTo(reloaded.get().getId());
	}

	@Test
	void findOptionalByEmail_isCaseSensitive() {
		memberRepository.save(newMember("Case@Example.com"));
		entityManager.flush();
		entityManager.clear();

		assertThat(memberRepository.findOptionalByEmail("Case@Example.com")).isPresent();
		assertThat(memberRepository.findOptionalByEmail("case@example.com")).isEmpty();
	}

	@Test
	void save_rejectsASecondMemberWithTheSameEmail() {
		memberRepository.saveAndFlush(newMember("dup@example.com"));

		assertThatThrownBy(() -> memberRepository.saveAndFlush(newMember("dup@example.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
