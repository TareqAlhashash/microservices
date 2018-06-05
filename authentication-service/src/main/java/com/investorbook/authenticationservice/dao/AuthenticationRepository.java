package com.investorbook.authenticationservice.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.investorbook.authenticationservice.dao.entities.AuthenticationEntity;

public interface AuthenticationRepository extends JpaRepository<AuthenticationEntity, String> {
	Optional<AuthenticationEntity> findOptionalByEmail(String email);
}
