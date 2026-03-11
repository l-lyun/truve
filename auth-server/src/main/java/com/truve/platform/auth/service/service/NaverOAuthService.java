package com.truve.platform.auth.service.service;

import static com.truve.platform.auth.service.domain.dto.response.OAuthDTO.*;

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
import com.truve.platform.auth.service.domain.entity.User;
import com.truve.platform.auth.service.repository.UserRepository;
import com.truve.platform.auth.service.security.JwtService;
import com.truve.platform.auth.service.security.TokenType;
import com.truve.platform.auth.service.security.properties.NaverOAuthProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NaverOAuthService {
	
	private final NaverOAuthProperties naverOAuthProperties;
	private final RestClient naverOAuthRestClient;
	private final RestClient naverApiRestClient;
	private final UserRepository userRepository;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;


	// TODO: 에러 코드에 따른 로직 처리
	// https://developers.naver.com/docs/login/devguide/devguide.md#3-1-1-%EC%84%9C%EB%B9%84%EC%8A%A4-%ED%99%98%EA%B2%BD-%ED%99%95%EC%9D%B8
	@Transactional
	public Pair<String, String> login(String code, String error, String errorDescription, String state) {

		NaverLoginResponse naverDTO = requestToken(code, state);

		Preconditions.validate(!(naverDTO == null), ErrorCode.NOT_FOUND_EMAIL);

		String naverAccessToken = naverDTO.getAccessToken();
		String naverRefreshToken = naverDTO.getRefreshToken();
		NaverUserInfo info = requestUserInfo(naverAccessToken);
		String naverEmail = info.getResponse().getEmail();
		String naverUserId = info.getResponse().getId();
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

	private NaverLoginResponse requestToken(String code, String state) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", naverOAuthProperties.getClientId());
		form.add("client_secret", naverOAuthProperties.getClientSecret());
		form.add("code", code);
		form.add("state", state);

		return naverOAuthRestClient.post()
			.uri("/token")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(NaverLoginResponse.class);
	}

	private NaverUserInfo requestUserInfo(String accessToken) {
		return naverApiRestClient.get()
			.uri("/me")
			.header("Authorization", "Bearer " + accessToken)
			.retrieve()
			.body(NaverUserInfo.class);
	}
}
