package com.truve.platform.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
			.addServersItem(new Server().url("/").description("Default Server"))
			.info(new Info()
				.title("Truve Platform API")
				.description("API 문서")
				.version("v1.0.0"));
	}
}
