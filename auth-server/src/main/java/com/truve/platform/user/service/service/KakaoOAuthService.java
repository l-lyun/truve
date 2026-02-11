package com.truve.platform.user.service.service;

import static com.truve.platform.user.service.domain.dto.response.OAuthDTO.*;

import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.user.service.domain.entity.User;
import com.truve.platform.user.service.repository.UserRepository;
import com.truve.platform.user.service.security.JwtService;
import com.truve.platform.user.service.security.TokenType;
import com.truve.platform.user.service.security.properties.KakaoOAuthProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KakaoOAuthService {
	private final KakaoOAuthProperties kakaoOAuthProperties;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final RestClient kakaoOauthRestClient;
	private final RestClient kakaoApiRestClient;
	private final UserRepository userRepository;


	// TODO: 에러 코드에 따른 로직 처리
	// https://developers.kakao.com/docs/latest/ko/kakaologin/trouble-shooting
	@Transactional
	public Pair<String, String> login(String code, String error, String errorDescription, String state) {

		KakaoLoginResponse kakaoDTO = requestToken(code);

		Preconditions.validate(!(kakaoDTO == null), ErrorCode.NOT_FOUND_EMAIL);

		String kakaoAccessToken = kakaoDTO.getAccessToken();
		String kakaoRefreshToken = kakaoDTO.getRefreshToken();
		KakaoUserInfo info = requestUserInfo(kakaoAccessToken);
		String kakaoEmail = info.getKakaoAccount().getEmail();
		String kakaoUserId = info.getId();
		User user;

		if (!userRepository.existsByEmail(kakaoEmail)) {
			user = User.createOAuthUser(kakaoEmail,AuthProvider.KAKAO, kakaoUserId, kakaoAccessToken, kakaoRefreshToken);
			userRepository.save(user);
		}
		else {
			user = userRepository.findByEmailOrThrow(kakaoEmail);
		}

		var accessExp = jwtService.getAccessExpiration();
		var refreshExp = jwtService.getRefreshExpiration();

		String accessToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), accessExp, TokenType.ACCESS_TOKEN.getType());

		String refreshToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), refreshExp,
			TokenType.REFRESH_TOKEN.getType());

		long refreshTtlMs = refreshExp.getTime() - System.currentTimeMillis();
		refreshTokenService.save(user.getId(), refreshToken, refreshTtlMs);

		return Pair.of(accessToken, refreshToken);
	}


	private KakaoLoginResponse requestToken(String code) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", kakaoOAuthProperties.getClientId());
		form.add("redirect_uri", kakaoOAuthProperties.getRedirectUri());
		form.add("code", code);
		form.add("client_secret", kakaoOAuthProperties.getClientSecret());

		return kakaoOauthRestClient.post()
			.uri("/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(KakaoLoginResponse.class);
	}

	private KakaoUserInfo requestUserInfo(String accessToken) {
		return kakaoApiRestClient.post()
			.uri("/user/me")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.header("Authorization", "Bearer " + accessToken)
			.body("property_keys=[\"kakao_account.email\"]")
			.retrieve()
			.body(KakaoUserInfo.class);
	}

}
