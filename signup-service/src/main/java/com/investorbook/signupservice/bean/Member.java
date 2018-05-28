package com.investorbook.signupservice.bean;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "all member details.")
@Entity
@Table(name = "members")
public class Member {
	@Id
	@Email
	private String email;

	@Size(min = 1, message = "firstname must be at least 1 char long")
	@ApiModelProperty(notes = "firstname should have at least 1 character")
	@Column(name = "firstname")
	private String firstName;

	@Size(min = 1, message = "lastname must be at least 1 char long")
	@ApiModelProperty(notes = "lastname should have at least 1 character")
	@Column(name = "lastname")
	private String lastName;

	// this is not stored, used to receive the password string entered from the UI
	@Transient
	@Size(min = 6, message = "password must be at least 6 char long")
	@NotNull(message = "password cannot be null")
	private String password;

	// salt+hash, never retrieve it back to the client
	@JsonIgnore
	@Column(name = "passwordhash")
	private byte[] passwordHash;

	public Member() {
		super();
	}

	public Member(String email, String firstName, String lastName, String password) {
		super();
		this.email = email;
		this.firstName = firstName;
		this.lastName = lastName;
		this.password = password;
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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public byte[] getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(byte[] passwordHash) {
		this.passwordHash = passwordHash;
	}

}
