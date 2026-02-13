package com.truve.platform.auth.service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.auth.service.domain.dto.request.EmailRequest;
import com.truve.platform.auth.service.service.EmailService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/email")
@Tag(name = "Email", description = "회원가입 시 이메일 인증 API")
public class EmailController {
	private final EmailService emailService;

	@PostMapping("/send-code")
	public ApiResult<Void> sendMail(
		@RequestBody @Valid EmailRequest.SendVerificationCode request
	) {
		emailService.sendMail(request.getEmail());
		return ApiResult.ok();
	}

	@PostMapping("/verify")
	public ApiResult<Void> verifyEmail(
		@RequestBody @Valid EmailRequest.VerifyCode request
	) {
		emailService.verifyEmail(request.getEmail(), request.getCode());
		return ApiResult.ok();
	}

}
