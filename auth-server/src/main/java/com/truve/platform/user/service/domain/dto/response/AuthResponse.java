package com.truve.platform.user.service.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class AuthResponse {

	@Getter
	@AllArgsConstructor
	@Schema(description = "로그인 응답, 헤더 내 refreshToken set-cookie")
	public static class Login {
		@Schema(description = "AccessToken(Authorization 헤더에 Bearer 토큰 사용)")
		public String accessToken;
	}
}
