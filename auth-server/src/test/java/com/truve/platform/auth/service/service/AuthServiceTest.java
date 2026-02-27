package com.truve.platform.auth.service.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Pair;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.auth.service.domain.entity.User;
import com.truve.platform.auth.service.repository.EmailVerificationRepository;
import com.truve.platform.auth.service.repository.UserRepository;
import com.truve.platform.auth.service.security.JwtService;
import com.truve.platform.common.constants.UserRole;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import io.jsonwebtoken.JwtException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private EmailVerificationRepository emailVerificationRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtService jwtService;
	@Mock
	private RefreshTokenService refreshTokenService;
	@Mock
	private AccessTokenBlacklistService accessTokenBlacklistService;

	@InjectMocks
	private AuthService authService;

	private User createUser(Long id, String email, String encodedPassword) {
		User user = User.createLocalUser(email, encodedPassword);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Nested
	@DisplayName("로그인 테스트")
	class LoginTest {

		@Test
		@DisplayName("이메일/비밀번호가 올바르면 액세스/리프레시 토큰을 발급한다.")
		void 로그인_성공() {
			// given
			String email = "user@test.com";
			String password = "plain";
			User user = createUser(1L, email, "encoded");
			Date accessExp = new Date(System.currentTimeMillis() + 60_000L);
			Date refreshExp = new Date(System.currentTimeMillis() + 120_000L);

			given(userRepository.findByEmailOrThrow(email)).willReturn(user);
			given(passwordEncoder.matches(password, user.getPassword())).willReturn(true);
			given(jwtService.getAccessExpiration()).willReturn(accessExp);
			given(jwtService.getRefreshExpiration()).willReturn(refreshExp);
			given(jwtService.issue(eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(accessExp), eq("access")))
				.willReturn("access-token");
			given(jwtService.issue(eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(refreshExp), eq("refresh")))
				.willReturn("refresh-token");

			// when
			Pair<String, String> result = authService.login(email, password);

			// then
			assertThat(result.getFirst()).isEqualTo("access-token");
			assertThat(result.getSecond()).isEqualTo("refresh-token");
			verify(refreshTokenService).save(eq(user.getId()), eq("refresh-token"), anyLong());
		}

		@Test
		@DisplayName("비밀번호가 틀리면 예외가 발생한다.")
		void 로그인_실패_비밀번호_불일치() {
			// given
			String email = "user@test.com";
			String password = "wrong";
			User user = createUser(1L, email, "encoded");

			given(userRepository.findByEmailOrThrow(email)).willReturn(user);
			given(passwordEncoder.matches(password, user.getPassword())).willReturn(false);

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.login(email, password));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_PASSWORD);
			verify(jwtService, never()).issue(anyLong(), anyString(), any(), any(), anyString());
			verify(refreshTokenService, never()).save(anyLong(), anyString(), anyLong());
		}
	}

	@Nested
	@DisplayName("토큰 재발급 테스트")
	class ReissueTest {

		@Test
		@DisplayName("유효한 리프레시 토큰이면 새 액세스/리프레시 토큰을 발급한다.")
		void 재발급_성공() {
			// given
			String refreshToken = "refresh-token";
			String email = "user@test.com";
			User user = createUser(1L, email, "encoded");
			Date newAccessExp = new Date(System.currentTimeMillis() + 60_000L);
			Date newRefreshExp = new Date(System.currentTimeMillis() + 120_000L);

			given(jwtService.parseEmail(refreshToken)).willReturn(email);
			given(userRepository.findByEmailOrThrow(email)).willReturn(user);
			given(jwtService.getAccessExpiration()).willReturn(newAccessExp);
			given(jwtService.getRefreshExpiration()).willReturn(newRefreshExp);
			given(jwtService.issue(eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(newAccessExp), eq("access")))
				.willReturn("new-access-token");
			given(jwtService.issue(eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(newRefreshExp), eq("refresh")))
				.willReturn("new-refresh-token");

			// when
			Pair<String, String> result = authService.reissue(refreshToken);

			// then
			assertThat(result.getFirst()).isEqualTo("new-access-token");
			assertThat(result.getSecond()).isEqualTo("new-refresh-token");
			verify(refreshTokenService).save(eq(user.getId()), eq("new-refresh-token"), anyLong());
		}

		@Test
		@DisplayName("리프레시 토큰 파싱에 실패하면 예외가 발생한다.")
		void 재발급_실패_유효하지_않은_토큰() {
			// given
			String refreshToken = "invalid-refresh-token";
			given(jwtService.parseEmail(refreshToken)).willThrow(new JwtException("invalid"));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.reissue(refreshToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
			verify(userRepository, never()).findByEmailOrThrow(anyString());
		}
	}

	@Nested
	@DisplayName("로그아웃 테스트")
	class LogoutTest {

		@Test
		@DisplayName("액세스 토큰이 유효하고 만료 전이면 블랙리스트에 등록한다.")
		void 로그아웃_성공() {
			// given
			Long userId = 1L;
			String accessToken = "access-token";
			String jti = "jti-123";
			Date exp = new Date(System.currentTimeMillis() + 60_000L);

			given(jwtService.parseJti(accessToken)).willReturn(jti);
			given(jwtService.parseExpiration(accessToken)).willReturn(exp);

			// when
			authService.logout(userId, accessToken);

			// then
			verify(refreshTokenService).delete(userId);
			verify(accessTokenBlacklistService).save(eq(jti), anyLong());
		}

		@Test
		@DisplayName("액세스 토큰이 이미 만료됐으면 블랙리스트 저장은 생략한다.")
		void 로그아웃_성공_만료된_토큰() {
			// given
			Long userId = 1L;
			String accessToken = "expired-access-token";
			String jti = "jti-123";
			Date expired = new Date(System.currentTimeMillis() - 1_000L);

			given(jwtService.parseJti(accessToken)).willReturn(jti);
			given(jwtService.parseExpiration(accessToken)).willReturn(expired);

			// when
			authService.logout(userId, accessToken);

			// then
			verify(refreshTokenService).delete(userId);
			verify(accessTokenBlacklistService, never()).save(anyString(), anyLong());
		}

		@Test
		@DisplayName("액세스 토큰 파싱에 실패하면 예외가 발생한다.")
		void 로그아웃_실패_유효하지_않은_토큰() {
			// given
			Long userId = 1L;
			String accessToken = "invalid-access-token";

			given(jwtService.parseJti(accessToken)).willThrow(new JwtException("invalid"));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.logout(userId, accessToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
			verify(refreshTokenService).delete(userId);
			verify(accessTokenBlacklistService, never()).save(anyString(), anyLong());
		}
	}

	@Nested
	@DisplayName("회원가입 테스트")
	class SignUpTest {

		@Test
		@DisplayName("이메일 인증이 완료되고 중복이 아니면 회원가입을 완료한다.")
		void 회원가입_성공() {
			// given
			String email = "new@test.com";
			String password = "plain";
			String verifiedAt = "1700000000000";

			given(emailVerificationRepository.isVerifiedEmail(email)).willReturn(verifiedAt);
			given(userRepository.existsByEmail(email)).willReturn(false);
			given(passwordEncoder.encode(password)).willReturn("encoded");

			// when
			authService.signUp(email, password);

			// then
			verify(userRepository).save(argThat(user ->
				user.getEmail().equals(email)
					&& user.getPassword().equals("encoded")
					&& user.getRole() == UserRole.MEMBER
			));
			verify(emailVerificationRepository).deleteVerifiedEmail(email);
		}

		@Test
		@DisplayName("인증되지 않은 이메일이면 예외가 발생한다.")
		void 회원가입_실패_미인증_이메일() {
			// given
			String email = "new@test.com";
			String password = "plain";

			given(emailVerificationRepository.isVerifiedEmail(email)).willReturn("");

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.signUp(email, password));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_VERIFIED_EMAIL);
			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("이미 가입된 이메일이면 예외가 발생한다.")
		void 회원가입_실패_중복_이메일() {
			// given
			String email = "dup@test.com";
			String password = "plain";

			given(emailVerificationRepository.isVerifiedEmail(email)).willReturn("1700000000000");
			given(userRepository.existsByEmail(email)).willReturn(true);

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.signUp(email, password));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXISTS_EMAIL);
			verify(userRepository, never()).save(any(User.class));
			verify(emailVerificationRepository, never()).deleteVerifiedEmail(anyString());
		}
	}
}
