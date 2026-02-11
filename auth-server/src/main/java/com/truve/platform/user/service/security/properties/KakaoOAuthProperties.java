package com.truve.platform.user.service.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "oauth.kakao")
public class KakaoOAuthProperties {

	private String clientId;
	private String redirectUri;
	private String clientSecret;
	private String tokenUrl;
	private String authorizationUrl;

}
