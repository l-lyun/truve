package com.truve.platform.auth.service.service;

import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.auth.service.domain.entity.User;
import com.truve.platform.auth.service.repository.UserRepository;
import com.truve.platform.auth.service.security.JwtService;
import com.truve.platform.auth.service.security.TokenType;
import com.truve.platform.auth.service.service.social.KakaoOAuthProviderClient;
import com.truve.platform.auth.service.service.social.OAuthTokenInfo;
import com.truve.platform.auth.service.service.social.OAuthUserInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KakaoOAuthService {
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final KakaoOAuthProviderClient kakaoOAuthProviderClient;
	private final UserRepository userRepository;


	// TODO: 에러 코드에 따른 로직 처리
	//  https://developers.kakao.com/docs/latest/ko/kakaologin/trouble-shooting
	@Transactional
	public Pair<String, String> login(String code, String error, String errorDescription, String state) {

		OAuthTokenInfo tokenInfo = kakaoOAuthProviderClient.exchangeToken(code, state);

		Preconditions.validate(tokenInfo != null, ErrorCode.NOT_FOUND_EMAIL);

		String kakaoAccessToken = tokenInfo.getAccessToken();
		String kakaoRefreshToken = tokenInfo.getRefreshToken();
		OAuthUserInfo userInfo = kakaoOAuthProviderClient.getUserInfo(kakaoAccessToken);
		Preconditions.validate(userInfo != null && userInfo.getEmail() != null, ErrorCode.NOT_FOUND_EMAIL);
		String kakaoEmail = userInfo.getEmail();
		String kakaoUserId = userInfo.getProviderUserId();
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

		String accessToken = jwtService.issue(
			user.getPublicId(), user.getId(), user.getEmail(), user.getRole(), accessExp, TokenType.ACCESS_TOKEN.getType()
		);

		String refreshToken = jwtService.issue(user.getPublicId(), user.getId(), user.getEmail(), user.getRole(), refreshExp,
			TokenType.REFRESH_TOKEN.getType());

		long refreshTtlMs = refreshExp.getTime() - System.currentTimeMillis();
		refreshTokenService.save(user.getPublicId(), refreshToken, refreshTtlMs);

		return Pair.of(accessToken, refreshToken);
	}
}
