package com.investorbook.apigateway.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/**
 * The OpenTelemetryAppender declared in logback-spring.xml is created by Logback long before
 * Spring has built the OpenTelemetry SDK, so it buffers log lines until it is handed the SDK.
 * Boot autoconfigures the SDK and the OTLP exporter but does not do this hand-over. This is the
 * twin of common's OpenTelemetryLogAppenderConfiguration, which the gateway can't depend on
 * (see CLAUDE.md, "api-gateway").
 */
@Configuration
public class OpenTelemetryLogAppenderConfig {

	@Bean
	InitializingBean installOpenTelemetryLogAppender(OpenTelemetry openTelemetry) {
		return () -> OpenTelemetryAppender.install(openTelemetry);
	}
}
