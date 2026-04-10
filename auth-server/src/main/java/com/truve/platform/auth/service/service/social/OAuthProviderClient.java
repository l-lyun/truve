package com.truve.platform.auth.service.service.social;

import com.truve.platform.common.constants.AuthProvider;

public interface OAuthProviderClient {

	AuthProvider supports();

	OAuthTokenInfo exchangeToken(String code, String state);

	OAuthUserInfo getUserInfo(String accessToken);
}
