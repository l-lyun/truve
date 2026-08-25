package org.truve.platform.ticketing.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = {
	"com.truve.platform",
	"org.truve.platform"
})
@EnableFeignClients
@EnableScheduling
public class TicketingApplication {

	public static void main(String[] args) {
		SpringApplication.run(
			TicketingApplication.class,
			args
		);
	}

}
