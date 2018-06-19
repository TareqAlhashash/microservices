package com.investorbook.apigateway.config;

import org.springframework.beans.factory.annotation.Value;

public class JwtAuthenticationConfig {

	@Value("${investorbook.security.html5.jwt.client.id}")
	private String html5ClientId;

	@Value("${investorbook.security.html5.jwt.client.secret}")
	private String html5ClientSecret;

	public String getHtml5ClientId() {
		return html5ClientId;
	}

	public void setHtml5ClientId(String html5ClientId) {
		this.html5ClientId = html5ClientId;
	}

	public String getHtml5ClientSecret() {
		return html5ClientSecret;
	}

	public void setHtml5ClientSecret(String html5ClientSecret) {
		this.html5ClientSecret = html5ClientSecret;
	}

}
