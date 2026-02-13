package com.truve.platform.auth.service.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthCookieManager {

	private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

	private static final boolean HTTP_ONLY = true;
	// TODO: SECURE True로 변경 후 배포
	private static final boolean SECURE = false;
	private static final String SAME_SITE = "Lax";
	private static final String PATH = "/";

	public void setRefreshToken(
		HttpServletResponse response,
		String refreshToken,
		long maxAgeSeconds
	) {
		ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
			.httpOnly(HTTP_ONLY)
			.secure(SECURE)
			.sameSite(SAME_SITE)
			.path(PATH)
			.maxAge(maxAgeSeconds)
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearRefreshToken(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
			.httpOnly(HTTP_ONLY)
			.secure(SECURE)
			.sameSite(SAME_SITE)
			.path(PATH)
			.maxAge(0)
			.build();

		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
