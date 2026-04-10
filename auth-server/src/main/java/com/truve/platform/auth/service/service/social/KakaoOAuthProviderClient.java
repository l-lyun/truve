package com.truve.platform.auth.service.service.social;

import static com.truve.platform.auth.service.domain.dto.response.OAuthDTO.*;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.truve.platform.auth.service.security.properties.KakaoOAuthProperties;
import com.truve.platform.common.constants.AuthProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KakaoOAuthProviderClient implements OAuthProviderClient {

	private final KakaoOAuthProperties kakaoOAuthProperties;
	private final RestClient kakaoOAuthRestClient;
	private final RestClient kakaoApiRestClient;

	@Override
	public AuthProvider supports() {
		return AuthProvider.KAKAO;
	}

	@Override
	public OAuthTokenInfo exchangeToken(String code, String state) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", kakaoOAuthProperties.getClientId());
		form.add("redirect_uri", kakaoOAuthProperties.getRedirectUri());
		form.add("code", code);
		form.add("client_secret", kakaoOAuthProperties.getClientSecret());

		KakaoLoginResponse response = kakaoOAuthRestClient.post()
			.uri("/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(KakaoLoginResponse.class);

		if (response == null) {
			return null;
		}

		return new OAuthTokenInfo(response.getAccessToken(), response.getRefreshToken());
	}

	@Override
	public OAuthUserInfo getUserInfo(String accessToken) {
		KakaoUserInfo response = kakaoApiRestClient.post()
			.uri("/user/me")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.header("Authorization", "Bearer " + accessToken)
			.body("property_keys=[\"kakao_account.email\"]")
			.retrieve()
			.body(KakaoUserInfo.class);

		if (response == null || response.getKakaoAccount() == null) {
			return null;
		}

		return new OAuthUserInfo(
			AuthProvider.KAKAO,
			response.getId(),
			response.getKakaoAccount().getEmail()
		);
	}
}
