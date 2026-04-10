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

		@NotBlank
		private String nickname;

		private boolean serviceTermsAgreed;

		private boolean electronicFinanceTermsAgreed;

		private boolean privacyCollectionAgreed;

		private boolean marketingInfoAgreed;

		private boolean over14Agreed;
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

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	@Schema(name = "AuthRequestChangeNickname", description = "닉네임 변경 요청")
	public static class ChangeNickname {
		@NotBlank
		private String nickname;
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	@Schema(name = "AuthRequestUpdateMarketingConsent", description = "마케팅 정보 수신 동의 변경 요청")
	public static class UpdateMarketingConsent {
		private boolean marketingInfoAgreed;
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	@Schema(name = "AuthRequestUpdateEmailNotificationConsent", description = "이메일 알림 수신 동의 변경 요청")
	public static class UpdateEmailNotificationConsent {
		private boolean emailNotificationAgreed;
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	@Schema(name = "AuthRequestCompleteSocialSignUp", description = "소셜 회원가입 완료 요청")
	public static class CompleteSocialSignUp {
		@NotBlank
		private String registrationToken;

		@NotBlank
		private String email;

		@NotBlank
		private String nickname;

		private boolean serviceTermsAgreed;

		private boolean electronicFinanceTermsAgreed;

		private boolean privacyCollectionAgreed;

		private boolean marketingInfoAgreed;

		private boolean over14Agreed;
	}
}
