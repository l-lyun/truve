package com.truve.platform.apigateway.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

	private static final List<String> ALLOWED_ORIGIN_PATTERNS = List.of(
		"http://localhost:[*]",
		"http://127.0.0.1:[*]",
		"https://front-nu-tawny.vercel.app"
	);
	private static final List<String> ALLOWED_HEADERS = List.of("*");
	private static final List<String> ALLOWED_METHODS = List.of("*");

	@Bean
	public CorsWebFilter corsWebFilter() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", getCorsConfiguration());
		return new CorsWebFilter(source);
	}

	private CorsConfiguration getCorsConfiguration() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowCredentials(true);
		config.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
		config.setAllowedHeaders(ALLOWED_HEADERS);
		config.setAllowedMethods(ALLOWED_METHODS);
		config.setMaxAge(3600L);
		return config;
	}
}