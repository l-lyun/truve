package com.truve.platform.user.service.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OAuthClientConfig {

	private final String KAKAO_OAUTH_URL = "https://kauth.kakao.com/oauth";

	@Bean
	public RestClient kakaoOauthRestClient() {
		return RestClient.builder()
			.baseUrl(KAKAO_OAUTH_URL)
			.build();
	}
}
