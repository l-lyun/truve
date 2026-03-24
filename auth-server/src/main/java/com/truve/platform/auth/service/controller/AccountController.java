package com.truve.platform.auth.service.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.auth.service.domain.dto.request.AuthRequest;
import com.truve.platform.auth.service.domain.dto.response.AuthResponse;
import com.truve.platform.auth.service.security.AuthCookieManager;
import com.truve.platform.auth.service.service.AuthService;
import com.truve.platform.common.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Account", description = "내 계정 API")
public class AccountController {
	private final AuthService authService;
	private final AuthCookieManager authCookieManager;

	@Operation(summary = "내 정보 조회")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "내 정보 조회 성공",
			content = @Content(
				schema = @Schema(implementation = AuthResponse.Me.class)
			)
		)
	})
	@GetMapping("/me")
	public ApiResult<AuthResponse.Me> getMe(
		@Parameter(hidden = true)
		@RequestHeader("X-Token") String accessToken
	) {
		return ApiResult.ok(authService.getMe(accessToken));
	}

	@Operation(summary = "닉네임 변경")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "닉네임 변경 성공"
		)
	})
	@PatchMapping("/me/nickname")
	public ApiResult<Void> changeNickname(
		@Parameter(hidden = true)
		@RequestHeader("X-Token") String accessToken,
		@RequestBody @Valid AuthRequest.ChangeNickname request
	) {
		authService.changeNickname(accessToken, request.getNickname());

		return ApiResult.ok();
	}

	@Operation(summary = "마케팅 정보 수신 동의 변경")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "마케팅 정보 수신 동의 변경 성공"
		)
	})
	@PatchMapping("/me/marketing-consent")
	public ApiResult<Void> updateMarketingConsent(
		@Parameter(hidden = true)
		@RequestHeader("X-Token") String accessToken,
		@RequestBody @Valid AuthRequest.UpdateMarketingConsent request
	) {
		authService.updateMarketingConsent(accessToken, request.isMarketingInfoAgreed());

		return ApiResult.ok();
	}

	@Operation(summary = "이메일 알림 수신 동의 변경")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "이메일 알림 수신 동의 변경 성공"
		)
	})
	@PatchMapping("/me/email-notification")
	public ApiResult<Void> updateEmailNotificationConsent(
		@Parameter(hidden = true)
		@RequestHeader("X-Token") String accessToken,
		@RequestBody @Valid AuthRequest.UpdateEmailNotificationConsent request
	) {
		authService.updateEmailNotificationConsent(accessToken, request.isEmailNotificationAgreed());

		return ApiResult.ok();
	}

	@Operation(summary = "회원 탈퇴")
	@ApiResponses({
		@ApiResponse(
			responseCode = "200",
			description = "회원 탈퇴 성공"
		),
	})
	@DeleteMapping("/me")
	public ApiResult<Void> withdraw(
		@Parameter(hidden = true)
		@RequestHeader("X-Token") String accessToken,
		HttpServletResponse httpServletResponse
	) {
		authService.withdraw(accessToken);
		authCookieManager.clearRefreshToken(httpServletResponse);

		return ApiResult.ok();
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
		@Parameter(hidden = true)
		@RequestHeader("X-Token") String accessToken,
		HttpServletResponse httpServletResponse
	) {

		authService.logout(accessToken);
		authCookieManager.clearRefreshToken(httpServletResponse);

		return ApiResult.ok();
	}
}
