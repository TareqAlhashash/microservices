package com.investorbook.apigateway.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investorbook.apigateway.dto.ServiceStatusResponse;
import com.investorbook.apigateway.service.DashboardService;

import reactor.core.publisher.Mono;

/**
 * Deliberately unauthenticated, like every /actuator/** endpoint in this repo (see
 * order-service's SecurityConfiguration and the README's "Observability" section) - see
 * DashboardService for the actual health-aggregation logic.
 */
@RestController
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/dashboard/services")
	public Mono<List<ServiceStatusResponse>> serviceStatuses() {
		return dashboardService.serviceStatuses();
	}
}
