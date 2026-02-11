package com.truve.platform.user.service.controller;

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
import com.truve.platform.user.service.domain.dto.request.AuthRequest;
import com.truve.platform.user.service.domain.dto.response.AuthResponse;
import com.truve.platform.user.service.security.AuthCookieManager;
import com.truve.platform.user.service.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;
	private final AuthCookieManager cookieManager;

	@PostMapping("/sign-up")
	public ApiResult<Void> signUp(
		@RequestBody  @Valid AuthRequest.SignUp request
	) {
		System.out.println(request.getEmail());
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
	public ApiResult<AuthResponse.Login> login(
		@RequestBody @Valid AuthRequest.Login request
	) {
		Pair<String,String> tokens = authService.login(request.getEmail(), request.getPassword());

		AuthResponse.Login res = new AuthResponse.Login(tokens.getFirst(), tokens.getSecond());
		return ApiResult.ok(res);
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
		HttpServletResponse response
	) {
		Pair<String, String> tokens = authService.reissue(refreshToken);

		String newAccessToken = tokens.getFirst();
		String newRefreshToken = tokens.getSecond();

		cookieManager.setRefreshToken(
			response,
			newRefreshToken,
			60L * 60 * 24 * 14
		);

		// TODO: 자체 로그인 리프레시 토큰 전달 방식 변경 시 DTO 같이 변경
		return ApiResult.ok(new AuthResponse.Login(newAccessToken, null));
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
		HttpServletResponse response
	) {

		authService.logout(Long.parseLong(userId), accessToken);

		cookieManager.clearRefreshToken(response);

		return ApiResult.ok();
	}


}
