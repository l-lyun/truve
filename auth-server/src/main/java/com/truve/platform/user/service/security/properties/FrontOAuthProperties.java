package com.truve.platform.user.service.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@ConfigurationProperties(prefix = "oauth.front")
public class FrontOAuthProperties {
	private String callback;
}
