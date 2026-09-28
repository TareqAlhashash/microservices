package com.investorbook.common.observation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;

class OpenTelemetryLogAppenderConfigurationTest {

	@Test
	void afterInstalling_logLinesReachTheOpenTelemetrySdk() throws Exception {
		List<LogRecordData> exported = new CopyOnWriteArrayList<>();
		OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setLoggerProvider(SdkLoggerProvider.builder()
				.addLogRecordProcessor(SimpleLogRecordProcessor.create(collectingInto(exported))).build()).build();

		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		OpenTelemetryAppender appender = new OpenTelemetryAppender();
		appender.setContext(context);
		appender.start();
		Logger logger = context.getLogger("otel-log-appender-test");
		logger.setAdditive(false);
		logger.addAppender(appender);
		try {
			new OpenTelemetryLogAppenderConfiguration().installOpenTelemetryLogAppender(sdk).afterPropertiesSet();

			logger.info("order placed");

			assertThat(exported).extracting(record -> record.getBodyValue().asString()).contains("order placed");
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	private static LogRecordExporter collectingInto(List<LogRecordData> target) {
		return new LogRecordExporter() {
			@Override
			public CompletableResultCode export(Collection<LogRecordData> logs) {
				target.addAll(logs);
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode flush() {
				return CompletableResultCode.ofSuccess();
			}

			@Override
			public CompletableResultCode shutdown() {
				return CompletableResultCode.ofSuccess();
			}
		};
	}
}
