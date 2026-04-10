package com.truve.platform.auth.service.service.social;

import com.truve.platform.common.constants.AuthProvider;

public record SocialRegistrationInfo(
	AuthProvider provider,
	String providerUserId,
	String email,
	String oAuthAccessToken,
	String oAuthRefreshToken
) {
}
