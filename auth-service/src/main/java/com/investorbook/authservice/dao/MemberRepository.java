package com.investorbook.authservice.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.authservice.dao.entities.MemberEntity;


public interface MemberRepository extends JpaRepository<MemberEntity, String> {
	Optional<MemberEntity> findOptionalByEmail(String email);
}
