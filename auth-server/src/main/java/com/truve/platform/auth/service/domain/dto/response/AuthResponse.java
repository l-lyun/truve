package com.truve.platform.auth.service.domain.dto.response;

import com.truve.platform.auth.service.domain.entity.User;

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

	@Getter
	@AllArgsConstructor
	@Schema(description = "내 정보 조회 응답")
	public static class Me {
		@Schema(description = "이메일")
		private final String email;

		@Schema(description = "닉네임")
		private final String nickname;

		@Schema(description = "마케팅 정보 수신 동의 여부")
		private final boolean marketingInfoAgreed;

		public static Me from(User user) {
			return new Me(
				user.getEmail(),
				user.getNickname(),
				user.isMarketingInfoAgreed()
			);
		}
	}
}
