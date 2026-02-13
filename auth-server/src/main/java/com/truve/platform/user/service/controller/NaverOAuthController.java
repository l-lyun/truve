package com.truve.platform.user.service.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.user.service.security.AuthCookieManager;
import com.truve.platform.user.service.security.properties.FrontOAuthProperties;
import com.truve.platform.user.service.security.properties.NaverOAuthProperties;
import com.truve.platform.user.service.service.NaverOAuthService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/naver")
public class NaverOAuthController {
	private final FrontOAuthProperties frontOAuthProperties;
	private final NaverOAuthProperties naverOAuthProperties;
	private final NaverOAuthService naverOAuthService;
	private final AuthCookieManager authCookieManager;

	@GetMapping("/login")
	public ResponseEntity<Void> login() {
		String redirectUri = naverOAuthProperties.getAuthorizationUrl()
			+ "?response_type=code&client_id=" + naverOAuthProperties.getClientId()
				// TODO: 유저 별 랜덤 문자열 레디스 저장 후 CSRF 방지
			+ "&state=" + UUID.randomUUID()
			+ "&redirect_uri=" + naverOAuthProperties.getRedirectUrl();

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
		Pair<String, String> tokens = naverOAuthService.login(code, error, error_description, state);
		String refreshToken = tokens.getSecond();

		authCookieManager.setRefreshToken(
			response,
			refreshToken,
			60L * 60 * 24 * 14
		);

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(frontOAuthProperties.getCallback()))
			.build();
	}
}
