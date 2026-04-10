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
import com.truve.platform.auth.service.service.social.NaverOAuthProviderClient;
import com.truve.platform.auth.service.service.social.OAuthTokenInfo;
import com.truve.platform.auth.service.service.social.OAuthUserInfo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NaverOAuthService {
	
	private final NaverOAuthProviderClient naverOAuthProviderClient;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;


	// TODO: 에러 코드에 따른 로직 처리
	// https://developers.naver.com/docs/login/devguide/devguide.md#3-1-1-%EC%84%9C%EB%B9%84%EC%8A%A4-%ED%99%98%EA%B2%BD-%ED%99%95%EC%9D%B8
	@Transactional
	public Pair<String, String> login(String code, String error, String errorDescription, String state) {

		OAuthTokenInfo tokenInfo = naverOAuthProviderClient.exchangeToken(code, state);

		Preconditions.validate(tokenInfo != null, ErrorCode.NOT_FOUND_EMAIL);

		String naverAccessToken = tokenInfo.getAccessToken();
		String naverRefreshToken = tokenInfo.getRefreshToken();
		OAuthUserInfo userInfo = naverOAuthProviderClient.getUserInfo(naverAccessToken);
		Preconditions.validate(userInfo != null && userInfo.getEmail() != null, ErrorCode.NOT_FOUND_EMAIL);
		String naverEmail = userInfo.getEmail();
		String naverUserId = userInfo.getProviderUserId();
		User user;

		if (!userRepository.existsByEmail(naverEmail)) {
			user = User.createOAuthUser(naverEmail,
				AuthProvider.NAVER, naverUserId, naverAccessToken, naverRefreshToken);
			userRepository.save(user);
		}
		else {
			user = userRepository.findByEmailOrThrow(naverEmail);
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
