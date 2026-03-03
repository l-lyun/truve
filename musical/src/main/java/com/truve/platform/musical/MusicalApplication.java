package com.truve.platform.musical;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MusicalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MusicalApplication.class, args);
    }
}
