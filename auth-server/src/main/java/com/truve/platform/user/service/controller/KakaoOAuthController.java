package com.truve.platform.user.service.controller;

import java.net.URI;

import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.user.service.security.AuthCookieManager;
import com.truve.platform.user.service.security.properties.KakaoOAuthProperties;
import com.truve.platform.user.service.service.KakaoOAuthService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/kakao")
@RequiredArgsConstructor
public class KakaoOAuthController {
	private final KakaoOAuthProperties kakaoOAuthProperties;
	private final KakaoOAuthService kakaoOAuthService;
	private final AuthCookieManager authCookieManager;

	@GetMapping("/login")
	public ResponseEntity<Void> login() {
		String redirectUri =
			kakaoOAuthProperties.getAuthorizationUrl()
				+ "?response_type=code&client_id=" + kakaoOAuthProperties.getClientId()
				+ "&redirect_uri=" + kakaoOAuthProperties.getRedirectUri();

		return ResponseEntity
			.status(HttpStatus.FOUND)
			.location(URI.create(redirectUri))
			.build();
	}

	@GetMapping("/callback")
	public ResponseEntity<Void> callback(
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String error,
		@RequestParam(required = false) String error_description,
		@RequestParam(required = false) String state,
		HttpServletResponse response
	) {
		Pair<String, String> tokens = kakaoOAuthService.login(code, error, error_description, state);

		String refreshToken = tokens.getSecond();

		authCookieManager.setRefreshToken(
			response,
			refreshToken,
			60L * 60 * 24 * 14
		);

		return ResponseEntity.status(HttpStatus.FOUND)
			// TODO: 프론트 연동시 url 변경
			.location(URI.create("http://localhost:8081/test/callback"))
			.build();
	}

}
