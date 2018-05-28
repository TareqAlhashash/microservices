package com.investorbook.signupservice.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.investorbook.signupservice.bean.MemberEntity;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, String> {

}
