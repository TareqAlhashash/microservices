package com.investorbook.apigateway.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * A plain (non-load-balanced) WebClient for server-to-server calls this app makes itself,
 * outside Gateway's own proxying - DashboardService polls every registered service's own
 * /actuator/health with it, and LoginService uses it for the single call to auth-service's
 * /login (resolved via ReactiveDiscoveryClient rather than Gateway's lb:// routing, since
 * that's only wired up for requests Gateway proxies end to end, not a call made from within a
 * service method). Short timeouts matter here specifically: a dead service must not make the
 * whole dashboard endpoint, or a login attempt, hang waiting on it.
 */
@Configuration
public class WebClientConfig {

	@Bean
	public WebClient internalWebClient() {
		HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
				.responseTimeout(Duration.ofMillis(1000));
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
	}
}
