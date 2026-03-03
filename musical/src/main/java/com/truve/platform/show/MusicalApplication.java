package com.truve.platform.show;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.truve.platform")
public class MusicalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicalApplication.class, args);
    }
}
