package com.investorbook.apigateway.dto;

/**
 * Only the one field of Boot Actuator's health response this dashboard actually needs (see
 * DashboardService). Public (not the private nested class it used to be) so
 * DashboardServiceTest can construct real instances to stub the RestTemplate with, rather
 * than a Map that would fail the generic checkcast ResponseEntity&lt;T&gt;.getBody() inserts at
 * the call site.
 */
public class ActuatorHealthResponse {

	private String status;

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
