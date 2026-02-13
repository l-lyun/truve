package com.truve.platform.auth.service.domain.dto.response;

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

	@Getter
	@NoArgsConstructor
	public static class NaverLoginResponse {

		@JsonProperty("refresh_token")
		public String refreshToken;

		@JsonProperty("access_token")
		public String accessToken;

		@JsonProperty("token_type")
		public String tokenType;

		@JsonProperty("expires_in")
		public String error;

		@JsonProperty("error_description")
		public String errorDescription;

	}

	@Getter
	@NoArgsConstructor
	public static class NaverUserInfo {

		private String resultcode;
		private String message;
		private Response response;

		@Getter
		@NoArgsConstructor
		public static class Response {

			private String id;
			private String email;
			private String name;
			private String nickname;
			private String gender;
			private String age;
			private String birthday;
			@JsonProperty("profile_image")
			private String profileImage;
			private String birthyear;
			private String mobile;
		}
	}

}
