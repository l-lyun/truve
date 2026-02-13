package com.truve.platform.auth.service.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AuthRequest {

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	@Schema(name = "AuthRequestSignUp", description = "자체 회원가입 요청")
	public static class SignUp {
		@NotBlank
		private String email;

		@NotBlank
		private String password;
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	@Schema(name = "AuthRequestLogin", description = "자체 로그인 요청")
	public static class Login {
		@NotBlank
		private String email;

		@NotBlank
		private String password;
	}
}
