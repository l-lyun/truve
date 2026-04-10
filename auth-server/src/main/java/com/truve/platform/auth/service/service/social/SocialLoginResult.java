package com.truve.platform.auth.service.service.social;

import com.truve.platform.common.constants.AuthProvider;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SocialLoginResult {

	public enum Status {
		LOGIN_SUCCESS,
		SIGN_UP_REQUIRED
	}

	private final Status status;
	private final String accessToken;
	private final String refreshToken;
	private final String registrationToken;
	private final String email;
	private final AuthProvider provider;

	public static SocialLoginResult loginSuccess(String accessToken, String refreshToken, AuthProvider provider) {
		return new SocialLoginResult(Status.LOGIN_SUCCESS, accessToken, refreshToken, null, null, provider);
	}

	public static SocialLoginResult signUpRequired(String registrationToken, String email, AuthProvider provider) {
		return new SocialLoginResult(Status.SIGN_UP_REQUIRED, null, null, registrationToken, email, provider);
	}
}
