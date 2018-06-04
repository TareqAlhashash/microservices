package com.investorbook.memberservice.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.investorbook.memberservice.dao.entites.MemberEntity;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, String> {

	Optional<MemberEntity> findOptionalByEmail(String email);
}
