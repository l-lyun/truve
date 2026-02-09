package com.truve.platform.user.service.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class EmailRequest {

	@Getter
	@AllArgsConstructor
	@Schema(name = "EmailRequestSendVerificationCode", description = "이메일 인증코드 전송")
	public static class SendVerificationCode {
		@NotBlank
		String email;
	}

	@Getter
	@AllArgsConstructor
	@Schema(name = "EmailRequestVerifyCode", description = "이메일 인증코드 인증")
	public static class VerifyCode {
		@Email
		@NotBlank
		String email;
		@NotBlank
		String code;

	}
}
