package com.truve.platform.auth.service.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "oauth.naver")
public class NaverOAuthProperties {
	private String clientId;
	private String clientSecret;
	private String redirectUrl;
	private String tokenUrl;
	private String authorizationUrl;
}


