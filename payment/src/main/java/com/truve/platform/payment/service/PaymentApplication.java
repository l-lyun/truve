package com.truve.platform.payment.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.truve.platform")
@ConfigurationPropertiesScan
@EnableAsync
public class PaymentApplication {
	public static void main(String[] args) {
		SpringApplication.run(PaymentApplication.class, args);
	}

}