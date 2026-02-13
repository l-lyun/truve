package com.truve.platform.auth.service.controller;

import org.springframework.data.util.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.auth.service.domain.dto.request.AuthRequest;
import com.truve.platform.auth.service.domain.dto.response.AuthResponse;
import com.truve.platform.auth.service.security.AuthCookieManager;
import com.truve.platform.auth.service.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "인증/인가 API")
public class AuthController {
	private final AuthService authService;
	private final AuthCookieManager authCookieManager;

	@PostMapping("/sign-up")
	public ApiResult<Void> signUp(
		@RequestBody  @Valid AuthRequest.SignUp request
	) {
		authService.signUp(request.getEmail(), request.getPassword());

		return ApiResult.ok();
	}

	@Operation(summary = "로그인")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			content = @Content(
				schema = @Schema(implementation = AuthResponse.Login.class)
			)
		),
	})
	@PostMapping("/login")
	public ResponseEntity<AuthResponse.Login> login(
		HttpServletResponse httpServletResponse,
		@RequestBody @Valid AuthRequest.Login request
	) {
		Pair<String,String> tokens = authService.login(request.getEmail(), request.getPassword());

		String accessToken = tokens.getFirst();
		String refreshToken = tokens.getSecond();

		var response = new AuthResponse.Login(accessToken);


		authCookieManager.setRefreshToken(
			httpServletResponse,
			refreshToken,
			60L * 60 * 24 * 14
			);

		return ResponseEntity.ok(response);
	}

	@Operation(summary = "토큰 재발급")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "Access Token 재발급 성공",
			content = @Content(
				schema = @Schema(implementation = AuthResponse.Login.class)
			)
		)
	})
	@PostMapping("/reissue")
	public ApiResult<AuthResponse.Login> reissue(
		@CookieValue(name = "refreshToken") String refreshToken,
		HttpServletResponse httpServletResponse
	) {
		Pair<String, String> tokens = authService.reissue(refreshToken);

		String newAccessToken = tokens.getFirst();
		String newRefreshToken = tokens.getSecond();

		authCookieManager.setRefreshToken(
			httpServletResponse,
			newRefreshToken,
			60L * 60 * 24 * 14
		);

		var response = new AuthResponse.Login(newAccessToken);

		return ApiResult.ok(response);
	}


	@Operation(summary = "로그아웃")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "로그아웃 성공"
		),
	})
	@DeleteMapping("/logout")
	public ApiResult<Void> logout(
		@RequestHeader("X-User-Id") String userId,
		@RequestHeader("X-Token") String accessToken,
		HttpServletResponse httpServletResponse
	) {

		authService.logout(Long.parseLong(userId), accessToken);

		authCookieManager.clearRefreshToken(httpServletResponse);

		return ApiResult.ok();
	}


}
