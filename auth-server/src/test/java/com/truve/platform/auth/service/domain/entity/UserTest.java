package com.truve.platform.auth.service.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.constants.UserRole;

class UserTest {

	private static final String EMAIL = "test@truve.com";
	private static final String PASSWORD = "encoded-password";
	private static final String OAUTH_USER_ID = "oauth-user-id";
	private static final String OAUTH_ACCESS_TOKEN = "oauth-access-token";
	private static final String OAUTH_REFRESH_TOKEN = "oauth-refresh-token";

	@Nested
	@DisplayName("로컬 회원 생성 테스트")
	class CreateLocalUserTest {

		@Test
		@DisplayName("createLocalUser 호출 시 LOCAL/MEMBER 권한으로 사용자를 생성한다.")
		void createLocalUser_success() {
			// when
			User user = User.createLocalUser(EMAIL, PASSWORD);

			// then
			assertAll(
				() -> assertThat(user.getEmail()).isEqualTo(EMAIL),
				() -> assertThat(user.getPassword()).isEqualTo(PASSWORD),
				() -> assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL),
				() -> assertThat(user.getRole()).isEqualTo(UserRole.MEMBER),
				() -> assertThat(user.getOAuthUserId()).isNull(),
				() -> assertThat(user.getOAuthAccessToken()).isNull(),
				() -> assertThat(user.getOAuthRefreshToken()).isNull()
			);
		}
	}

	@Nested
	@DisplayName("OAuth 회원 생성 테스트")
	class CreateOAuthUserTest {

		@Test
		@DisplayName("createOAuthUser 호출 시 provider/MEMBER 및 OAuth 식별값을 저장한다.")
		void createOAuthUser_success() {
			// when
			User user = User.createOAuthUser(
				EMAIL,
				AuthProvider.KAKAO,
				OAUTH_USER_ID,
				OAUTH_ACCESS_TOKEN,
				OAUTH_REFRESH_TOKEN
			);

			// then
			assertAll(
				() -> assertThat(user.getEmail()).isEqualTo(EMAIL),
				() -> assertThat(user.getPassword()).isNull(),
				() -> assertThat(user.getProvider()).isEqualTo(AuthProvider.KAKAO),
				() -> assertThat(user.getRole()).isEqualTo(UserRole.MEMBER),
				() -> assertThat(user.getOAuthUserId()).isEqualTo(OAUTH_USER_ID),
				() -> assertThat(user.getOAuthAccessToken()).isEqualTo(OAUTH_ACCESS_TOKEN),
				() -> assertThat(user.getOAuthRefreshToken()).isEqualTo(OAUTH_REFRESH_TOKEN)
			);
		}
	}
}
