package com.investorbook.apigateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit routes, not the discovery locator: Spring Cloud Gateway's discovery locator needs its
 * own predicate/filter SpEL expressions configured by hand to auto-generate a route per
 * Eureka-registered service - there's no built-in default that just works. With only one service
 * real clients ever call through this gateway, an explicit route is less code and easier to
 * verify than that SpEL config would be.
 *
 * stripPrefix(1): a client calling /order-service/products reaches order-service's own /products
 * endpoint, not /order-service/products - order-service knows nothing about the path prefix its
 * gateway route happens to use.
 */
@Configuration
public class GatewayRoutesConfig {

	@Bean
	RouteLocator routes(RouteLocatorBuilder builder) {
		return builder.routes()
				.route("order-service",
						r -> r.path("/order-service/**").filters(f -> f.stripPrefix(1)).uri("lb://order-service"))
				.build();
	}
}
