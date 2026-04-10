package com.truve.platform.auth.service.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.auth.service.security.AuthCookieManager;
import com.truve.platform.auth.service.security.properties.FrontOAuthProperties;
import com.truve.platform.auth.service.security.properties.KakaoOAuthProperties;
import com.truve.platform.auth.service.service.SocialLoginService;
import com.truve.platform.auth.service.service.social.SocialLoginResult;
import com.truve.platform.common.constants.AuthProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/kakao")
@RequiredArgsConstructor
@Tag(name = "OAuth - KAKAO", description = "카카오 OAuth 리다이렉트")
public class KakaoOAuthController {
	private final FrontOAuthProperties frontOAuthProperties;
	private final KakaoOAuthProperties kakaoOAuthProperties;
	private final SocialLoginService socialLoginService;
	private final AuthCookieManager authCookieManager;

	@Operation(
		summary = "카카오 로그인 시작 (Redirect)",
		description = "브라우저를 카카오 인가 페이지로 302 리다이렉트합니다. (Swagger에서 실행해도 브라우저 이동이 정상적이지 않을 수 있습니다.)"
	)
	@ApiResponse(
		responseCode = "302",
		description = "카카오 Authorization URL로 Redirect",
		headers = @Header(name = "Location", description = "카카오 인가 URL")
	)
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

	@Operation(
		summary = "카카오 콜백 (Redirect)",
		description = """
  카카오가 redirect_uri로 전달한 code를 받아 서버에서 토큰 교환 후,
  refreshToken을 HttpOnly 쿠키로 설정하고 프론트 콜백 URL로 302 리다이렉트합니다.
  """
	)
	@ApiResponse(
		responseCode = "302",
		description = "프론트 콜백 URL로 Redirect",
		headers = @Header(name = "Location", description = "프론트 콜백 URL")
	)
	@GetMapping("/callback")
	public ResponseEntity<Void> callback(
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String error,
		@RequestParam(required = false) String error_description,
		@RequestParam(required = false) String state,
		HttpServletResponse httpServletResponse
	) {
		SocialLoginResult result = socialLoginService.start(AuthProvider.KAKAO, code, state);

		if (result.getStatus() == SocialLoginResult.Status.LOGIN_SUCCESS) {
			authCookieManager.setRefreshToken(
				httpServletResponse,
				result.getRefreshToken(),
				60L * 60 * 24 * 14
			);

			return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(frontOAuthProperties.getCallback() + "?status=login-success"))
				.build();
		}

		String redirectUri = frontOAuthProperties.getCallback()
			+ "?status=sign-up-required"
			+ "&provider=" + result.getProvider().name()
			+ "&email=" + URLEncoder.encode(result.getEmail(), StandardCharsets.UTF_8)
			+ "&registrationToken=" + URLEncoder.encode(result.getRegistrationToken(), StandardCharsets.UTF_8);

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(redirectUri))
			.build();
	}

}
