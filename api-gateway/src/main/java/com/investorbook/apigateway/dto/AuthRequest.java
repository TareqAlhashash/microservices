package com.investorbook.apigateway.dto;

import java.util.HashMap;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;


public class AuthRequest {

	
	private String grantType;
	
	@NotNull(message = "username cannot be null")
	private String username;

	@Size(min = 6, message = "password must be at least 6 char long")
	@NotNull(message = "password cannot be null")
	private String password;
	
	
	
	public AuthRequest() {
		super();
	}

	public AuthRequest(String username, String password) {
		super();
		this.username = username;
		this.password = password;
	}
	
	
	

	public String getGrantType() {
		return grantType;
	}

	public void setGrantType(String grantType) {
		this.grantType = grantType;
	}

	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	
	public Map<String, ?> toFormParams() {
		Map<String, String> params =  new HashMap<>() ;
		params.put("username", this.username);
		params.put("password", this.password);
		params.put("grant_type", "password");
		return params;
	}
	
}
