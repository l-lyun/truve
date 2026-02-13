package com.truve.platform.auth.service.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "oauth.front")
public class FrontOAuthProperties {
	private String callback;
}
