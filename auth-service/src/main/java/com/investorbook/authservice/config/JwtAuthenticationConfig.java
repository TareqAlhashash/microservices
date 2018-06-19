package com.investorbook.authservice.config;

import org.springframework.beans.factory.annotation.Value;

public class JwtAuthenticationConfig {

	@Value("${investorbook.security.jwt.url:/login}")
	private String url;

	@Value("${investorbook.security.jwt.header:Authorization}")
	private String header;

	@Value("${investorbook.security.jwt.prefix:Bearer}")
	private String prefix;

	@Value("${investorbook.security.jwt.expiration:#{24*60*60}}")
	private int expiration; // default 24 hours

	@Value("${investorbook.security.jwt.refresh.expiration:#{120*24*60*60}}")
	private int refreshExpiration; // 3 months
	
	@Value("${investorbook.security.jwt.public.key}")
	private String publicKey;
	
	@Value("${investorbook.security.jwt.private.key}")
	private String privateKey;

	@Value("${investorbook.security.html5.jwt.client.id}")
	private String html5ClientId;
	
	@Value("${investorbook.security.html5.jwt.client.secret}")
	private String html5ClientSecret;
	
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getHeader() {
		return header;
	}

	public void setHeader(String header) {
		this.header = header;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public int getExpiration() {
		return expiration;
	}

	public void setExpiration(int expiration) {
		this.expiration = expiration;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public int getRefreshExpiration() {
		return refreshExpiration;
	}

	public void setRefreshExpiration(int refreshExpiration) {
		this.refreshExpiration = refreshExpiration;
	}

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
