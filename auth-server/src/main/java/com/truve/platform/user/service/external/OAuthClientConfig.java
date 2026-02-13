package com.truve.platform.user.service.external;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OAuthClientConfig {

	private final String KAKAO_OAUTH_URL = "https://kauth.kakao.com/oauth";
	private final String KAKAO_API_URL = "https://kapi.kakao.com/v2";
	private final String NAVER_OAUTH_URL = "https://nid.naver.com/oauth2.0";
	private final String NAVER_API_URL = "https://openapi.naver.com/v1/nid";

	@Bean
	public RestClient kakaoOAuthRestClient() {
		return RestClient.builder()
			.baseUrl(KAKAO_OAUTH_URL)
			.build();
	}

	@Bean
	public RestClient kakaoApiRestClient() {
		return RestClient.builder()
			.baseUrl(KAKAO_API_URL)
			.build();
	}

	@Bean
	public RestClient naverOAuthRestClient() {
		return RestClient.builder()
			.baseUrl(NAVER_OAUTH_URL)
			.build();
	}

	@Bean
	public RestClient naverApiRestClient() {
		return RestClient.builder()
			.baseUrl(NAVER_API_URL)
			.build();
	}
}
