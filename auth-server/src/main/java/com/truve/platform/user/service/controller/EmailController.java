package com.truve.platform.user.service.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.user.service.domain.dto.request.EmailRequest;
import com.truve.platform.user.service.service.EmailService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/email")
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
