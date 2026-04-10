package com.truve.platform.auth.service.service.social;

import com.truve.platform.common.constants.AuthProvider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OAuthUserInfo {
	private final AuthProvider provider;
	private final String providerUserId;
	private final String email;
}
