package com.investorbook.memberservice.dao.entites;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.Email;

import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "members")
public class MemberEntity {
	
	@Id
	@GenericGenerator(name = "user_id", strategy = "com.investorbook.memberservice.dao.MemberIdGenerator")
    @GeneratedValue(generator = "user_id")
	@Column(name = "member_id")
	private String memberId;
	
	@Email
	@Column(unique=true)
	private String email;

	@Column(name = "firstname")
	private String firstName;

	@Column(name = "lastname")
	private String lastName;

	@Column(name = "passwordhash")
	private byte[] passwordHash;

	public MemberEntity() {
		super();
	}

	public MemberEntity(String email, String firstName, String lastName) {
		super();
		this.email = email;
		this.firstName = firstName;
		this.lastName = lastName;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public byte[] getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(byte[] passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getMemberId() {
		return memberId;
	}

	
	
}
