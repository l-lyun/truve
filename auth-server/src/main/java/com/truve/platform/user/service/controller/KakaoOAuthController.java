package com.truve.platform.user.service.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.user.service.security.properties.KakaoOAuthProperties;
import com.truve.platform.user.service.service.KakaoOAuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/kakao")
@RequiredArgsConstructor
public class KakaoOAuthController {
	private final KakaoOAuthProperties kakaoOAuthProperties;
	private final KakaoOAuthService kakaoOAuthService;
	@GetMapping("/login")
	public ResponseEntity<Void> login() {
		System.out.println("login");
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
	public ApiResult<Void> callback(
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String error,
		@RequestParam(required = false) String error_description,
		@RequestParam(required = false) String state
	) {
		kakaoOAuthService.login(code, error, error_description, state);
		return ApiResult.ok();
	}

}
