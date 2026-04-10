package com.truve.platform.auth.service.service.social;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OAuthTokenInfo {
	private final String accessToken;
	private final String refreshToken;
}
