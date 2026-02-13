package com.truve.platform.auth.service.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.auth.service.security.AuthCookieManager;
import com.truve.platform.auth.service.security.properties.FrontOAuthProperties;
import com.truve.platform.auth.service.security.properties.NaverOAuthProperties;
import com.truve.platform.auth.service.service.NaverOAuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/naver")
@Tag(name = "OAuth - NAVER", description = "네이버 OAuth 리다이렉트")
public class NaverOAuthController {
	private final FrontOAuthProperties frontOAuthProperties;
	private final NaverOAuthProperties naverOAuthProperties;
	private final NaverOAuthService naverOAuthService;
	private final AuthCookieManager authCookieManager;

	@Operation(
		summary = "네이버 로그인 시작 (Redirect)",
		description = "브라우저를 네이버 인가 페이지로 302 리다이렉트합니다. (Swagger에서 실행해도 브라우저 이동이 정상적이지 않을 수 있습니다.)"
	)
	@ApiResponse(
		responseCode = "302",
		description = "네이버 Authorization URL로 Redirect",
		headers = @Header(name = "Location", description = "네이버 인가 URL")
	)
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

	@Operation(
		summary = "네이버 콜백 (Redirect)",
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
		Pair<String, String> tokens = naverOAuthService.login(code, error, error_description, state);
		String refreshToken = tokens.getSecond();

		authCookieManager.setRefreshToken(
			httpServletResponse,
			refreshToken,
			60L * 60 * 24 * 14
		);

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(frontOAuthProperties.getCallback()))
			.build();
	}
}
