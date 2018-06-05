package com.investorbook.authenticationservice.dao.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Email;


/**
 * a simpler version of the member entity used only for authentication
 * 
 * @author tareq
 *
 */
@Entity
@Table(name = "members")
public class AuthenticationEntity {

	@Id
	@Column(name = "member_id")
	private String memberId;
	
	@Email
	@Column(unique = true)
	private String email;

	@Column(name = "passwordhash")
	private byte[] passwordHash;

	public AuthenticationEntity() {
		super();
	}

	public AuthenticationEntity(@Email String email, byte[] passwordHash) {
		super();
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public byte[] getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(byte[] passwordHash) {
		this.passwordHash = passwordHash;
	}

}
