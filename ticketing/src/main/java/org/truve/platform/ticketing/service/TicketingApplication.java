package org.truve.platform.ticketing.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = "org.truve.platform")
public class TicketingApplication {

	public static void main(String[] args) {
		SpringApplication.run(
			TicketingApplication.class,
			args
		);
	}

}
