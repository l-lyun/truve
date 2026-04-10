package com.truve.platform.auth.service.service.social;

import static com.truve.platform.auth.service.domain.dto.response.OAuthDTO.*;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.truve.platform.auth.service.security.properties.NaverOAuthProperties;
import com.truve.platform.common.constants.AuthProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NaverOAuthProviderClient implements OAuthProviderClient {

	private final NaverOAuthProperties naverOAuthProperties;
	private final RestClient naverOAuthRestClient;
	private final RestClient naverApiRestClient;

	@Override
	public AuthProvider supports() {
		return AuthProvider.NAVER;
	}

	@Override
	public OAuthTokenInfo exchangeToken(String code, String state) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", naverOAuthProperties.getClientId());
		form.add("client_secret", naverOAuthProperties.getClientSecret());
		form.add("code", code);
		form.add("state", state);

		NaverLoginResponse response = naverOAuthRestClient.post()
			.uri("/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(NaverLoginResponse.class);

		if (response == null) {
			return null;
		}

		return new OAuthTokenInfo(response.getAccessToken(), response.getRefreshToken());
	}

	@Override
	public OAuthUserInfo getUserInfo(String accessToken) {
		NaverUserInfo response = naverApiRestClient.get()
			.uri("/me")
			.header("Authorization", "Bearer " + accessToken)
			.retrieve()
			.body(NaverUserInfo.class);

		if (response == null || response.getResponse() == null) {
			return null;
		}

		return new OAuthUserInfo(
			AuthProvider.NAVER,
			response.getResponse().getId(),
			response.getResponse().getEmail()
		);
	}
}
