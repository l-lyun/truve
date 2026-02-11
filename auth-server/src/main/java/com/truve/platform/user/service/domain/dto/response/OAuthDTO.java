package com.truve.platform.user.service.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class OAuthDTO {
	@Getter
	@AllArgsConstructor
	public static class KakaoLoginResponse {
		@JsonProperty("token_type")
		public String tokenType;

		@JsonProperty("access_token")
		public String accessToken;

		@JsonProperty("id_token")
		public String idToken;

		@JsonProperty("expires_in")
		public Integer expiresIn;

		@JsonProperty("refresh_token")
		public String refreshToken;

		@JsonProperty("refresh_token_expires_in")
		public Integer refreshTokenExpiresIn;

		public String scope;
	}
	@Getter
	@NoArgsConstructor
	public static class KakaoUserInfo {

		private String id;

		@JsonProperty("kakao_account")
		private KakaoAccount kakaoAccount;

		@Getter
		@NoArgsConstructor
		public static class KakaoAccount {

			private String email;

			@JsonProperty("email_needs_agreement")
			private Boolean emailNeedsAgreement;

			@JsonProperty("is_email_valid")
			private Boolean isEmailValid;

			@JsonProperty("is_email_verified")
			private Boolean isEmailVerified;
		}
	}

}
