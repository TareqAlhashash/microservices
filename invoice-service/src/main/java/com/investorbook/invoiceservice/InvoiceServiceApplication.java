package com.investorbook.invoiceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

import com.investorbook.common.observation.InfrastructureObservationConfiguration;
import com.investorbook.common.observation.OpenTelemetryLogAppenderConfiguration;

@SpringBootApplication
@EnableDiscoveryClient
@Import({ InfrastructureObservationConfiguration.class, OpenTelemetryLogAppenderConfiguration.class })
public class InvoiceServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InvoiceServiceApplication.class, args);
	}
}
