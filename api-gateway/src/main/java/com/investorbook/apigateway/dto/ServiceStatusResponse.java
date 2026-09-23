package com.investorbook.apigateway.dto;

public class ServiceStatusResponse {

	private String name;
	private String status;
	private String url;

	public ServiceStatusResponse() {
		super();
	}

	public ServiceStatusResponse(String name, String status, String url) {
		this.name = name;
		this.status = status;
		this.url = url;
	}

	public String getName() {
		return name;
	}

	public String getStatus() {
		return status;
	}

	public String getUrl() {
		return url;
	}
}
