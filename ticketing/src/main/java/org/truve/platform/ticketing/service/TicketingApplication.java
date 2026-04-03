package org.truve.platform.ticketing.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = {
	"com.truve.platform",
	"org.truve.platform"
})
@EnableFeignClients
public class TicketingApplication {

	public static void main(String[] args) {
		SpringApplication.run(
			TicketingApplication.class,
			args
		);
	}

}
