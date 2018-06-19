package com.investorbook.authservice.dao.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
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
public class MemberEntity {

	@Id
	@Column(name = "id")
	private String id;
	
	@Email
	@Column(unique = true)
	private String email;

	@Column(name = "password_hash")
	private String passwordHash;

	public MemberEntity() {
		super();
	}

	public MemberEntity(@Email String email, String passwordHash) {
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

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

}
