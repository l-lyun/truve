package com.truve.platform.user.service.controller;

import org.springframework.data.util.Pair;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.user.service.domain.dto.request.AuthRequest;
import com.truve.platform.user.service.domain.dto.response.AuthResponse;
import com.truve.platform.user.service.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;

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

}
