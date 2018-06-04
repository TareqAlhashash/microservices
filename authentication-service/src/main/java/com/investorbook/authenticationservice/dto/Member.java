package com.investorbook.authenticationservice.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "all member details.")
public class Member {
	
	@ApiModelProperty(notes = "member Id")
	private String memberId;

	@ApiModelProperty(notes = "Email Address")
	private String email;

	@Size(min = 1, message = "firstname must be at least 1 char long")
	@ApiModelProperty(notes = "firstname should have at least 1 character")
	private String firstName;

	@Size(min = 1, message = "lastname must be at least 1 char long")
	@ApiModelProperty(notes = "lastname should have at least 1 character")
	private String lastName;

	@Size(min = 6, message = "password must be at least 6 char long")
	@NotNull(message = "password cannot be null")
	private String password;

	protected Member() {
		super();
	}

	public Member(String memberId, String email, String firstName, String lastName, String password) {
		super();
		this.memberId = memberId;
		this.email = email;
		this.firstName = firstName;
		this.lastName = lastName;
		this.password = password;
	}

	

	public String getMemberId() {
		return memberId;
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
}
