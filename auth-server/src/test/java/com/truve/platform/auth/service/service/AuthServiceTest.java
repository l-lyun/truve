package com.truve.platform.auth.service.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Pair;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.auth.service.domain.dto.response.AuthResponse;
import com.truve.platform.auth.service.domain.entity.User;
import com.truve.platform.auth.service.event.UserSignedUpEvent;
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
	@Mock
	private UserSignedUpEventPublisher userSignedUpEventPublisher;

	@InjectMocks
	private AuthService authService;

	private static final String NICKNAME = "tester";

	private User createUser(Long id, String email, String encodedPassword) {
		User user = User.createLocalUser(email, NICKNAME, encodedPassword, true, true, true, false, false, true);
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	@Nested
	@DisplayName("내 정보 조회 테스트")
	class GetMeTest {

		@Test
		@DisplayName("유효한 액세스 토큰이면 내 정보를 반환한다.")
		void 내정보조회_성공() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			AuthResponse.Me result = authService.getMe(accessToken);

			// then
			assertThat(result.getEmail()).isEqualTo(user.getEmail());
			assertThat(result.getNickname()).isEqualTo(user.getNickname());
			assertThat(result.isMarketingInfoAgreed()).isEqualTo(user.isMarketingInfoAgreed());
			assertThat(result.isEmailNotificationAgreed()).isEqualTo(user.isEmailNotificationAgreed());
		}

		@Test
		@DisplayName("액세스 토큰 파싱에 실패하면 예외가 발생한다.")
		void 내정보조회_실패_유효하지_않은_토큰() {
			// given
			String accessToken = "invalid-access-token";
			given(jwtService.parsePublicId(accessToken)).willThrow(new JwtException("invalid"));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.getMe(accessToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
			verify(userRepository, never()).findByPublicId(any());
		}

		@Test
		@DisplayName("토큰은 유효하지만 사용자가 없으면 예외가 발생한다.")
		void 내정보조회_실패_사용자없음() {
			// given
			String accessToken = "access-token";
			UUID userPublicId = UUID.randomUUID();

			given(jwtService.parsePublicId(accessToken)).willReturn(userPublicId);
			given(userRepository.findByPublicId(userPublicId)).willReturn(java.util.Optional.empty());

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.getMe(accessToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_USER);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 예외가 발생한다.")
		void 내정보조회_실패_탈퇴회원() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.withdraw();

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.getMe(accessToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_WITHDRAWN_USER);
		}
	}

	@Nested
	@DisplayName("닉네임 변경 테스트")
	class ChangeNicknameTest {

		@Test
		@DisplayName("유효한 닉네임이면 변경한다.")
		void 닉네임변경_성공() {
			// given
			String accessToken = "access-token";
			String newNickname = "newtester";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));
			given(userRepository.existsByNickname(newNickname)).willReturn(false);

			// when
			authService.changeNickname(accessToken, newNickname);

			// then
			assertThat(user.getNickname()).isEqualTo(newNickname);
		}

		@Test
		@DisplayName("영문 16자 닉네임이면 변경한다.")
		void 닉네임변경_성공_영문16자() {
			// given
			String accessToken = "access-token";
			String newNickname = "abcdefghijklmnop";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));
			given(userRepository.existsByNickname(newNickname)).willReturn(false);

			// when
			authService.changeNickname(accessToken, newNickname);

			// then
			assertThat(user.getNickname()).isEqualTo(newNickname);
		}

		@Test
		@DisplayName("현재 닉네임과 같으면 중복 조회 없이 유지한다.")
		void 닉네임변경_성공_동일닉네임() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			authService.changeNickname(accessToken, user.getNickname());

			// then
			assertThat(user.getNickname()).isEqualTo(NICKNAME);
			verify(userRepository, never()).existsByNickname(anyString());
		}

		@Test
		@DisplayName("닉네임 형식이 유효하지 않으면 예외가 발생한다.")
		void 닉네임변경_실패_형식오류() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.changeNickname(accessToken, "a b")
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_NICKNAME);
			verify(userRepository, never()).existsByNickname(anyString());
		}

		@Test
		@DisplayName("영문 17자 닉네임이면 예외가 발생한다.")
		void 닉네임변경_실패_영문17자() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.changeNickname(accessToken, "abcdefghijklmnopq")
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_NICKNAME);
			verify(userRepository, never()).existsByNickname(anyString());
		}

		@Test
		@DisplayName("이미 사용 중인 닉네임이면 예외가 발생한다.")
		void 닉네임변경_실패_중복닉네임() {
			// given
			String accessToken = "access-token";
			String newNickname = "dupname";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));
			given(userRepository.existsByNickname(newNickname)).willReturn(true);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.changeNickname(accessToken, newNickname)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXISTS_NICKNAME);
		}

		@Test
		@DisplayName("탈퇴한 회원이면 예외가 발생한다.")
		void 닉네임변경_실패_탈퇴회원() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.withdraw();

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.changeNickname(accessToken, "newtester")
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_WITHDRAWN_USER);
		}
	}

	@Nested
	@DisplayName("마케팅 정보 수신 동의 변경 테스트")
	class UpdateMarketingConsentTest {

		@Test
		@DisplayName("마케팅 정보 수신 동의 상태를 변경한다.")
		void 마케팅수신동의변경_성공() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			authService.updateMarketingConsent(accessToken, true);

			// then
			assertThat(user.isMarketingInfoAgreed()).isTrue();
		}

		@Test
		@DisplayName("마케팅 정보 수신 동의를 철회할 수 있다.")
		void 마케팅수신동의변경_성공_철회() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.updateMarketingInfoAgreed(true);

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			authService.updateMarketingConsent(accessToken, false);

			// then
			assertThat(user.isMarketingInfoAgreed()).isFalse();
		}

		@Test
		@DisplayName("탈퇴한 회원이면 예외가 발생한다.")
		void 마케팅수신동의변경_실패_탈퇴회원() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.withdraw();

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.updateMarketingConsent(accessToken, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_WITHDRAWN_USER);
		}
	}

	@Nested
	@DisplayName("이메일 알림 수신 동의 변경 테스트")
	class UpdateEmailNotificationConsentTest {

		@Test
		@DisplayName("이메일 알림 수신 동의 상태를 변경한다.")
		void 이메일알림수신동의변경_성공() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			authService.updateEmailNotificationConsent(accessToken, true);

			// then
			assertThat(user.isEmailNotificationAgreed()).isTrue();
		}

		@Test
		@DisplayName("이메일 알림 수신 동의를 철회할 수 있다.")
		void 이메일알림수신동의변경_성공_철회() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.updateEmailNotificationAgreed(true);

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			authService.updateEmailNotificationConsent(accessToken, false);

			// then
			assertThat(user.isEmailNotificationAgreed()).isFalse();
		}

		@Test
		@DisplayName("토큰은 유효하지만 사용자가 없으면 예외가 발생한다.")
		void 이메일알림수신동의변경_실패_사용자없음() {
			// given
			String accessToken = "access-token";
			UUID userPublicId = UUID.randomUUID();

			given(jwtService.parsePublicId(accessToken)).willReturn(userPublicId);
			given(userRepository.findByPublicId(userPublicId)).willReturn(java.util.Optional.empty());

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.updateEmailNotificationConsent(accessToken, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_USER);
		}

		@Test
		@DisplayName("유효하지 않은 액세스 토큰이면 예외가 발생한다.")
		void 이메일알림수신동의변경_실패_유효하지_않은_토큰() {
			// given
			String accessToken = "invalid-access-token";
			given(jwtService.parsePublicId(accessToken)).willThrow(new JwtException("invalid"));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.updateEmailNotificationConsent(accessToken, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
			verify(userRepository, never()).findByPublicId(any());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 예외가 발생한다.")
		void 이메일알림수신동의변경_실패_탈퇴회원() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.withdraw();

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.updateEmailNotificationConsent(accessToken, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_WITHDRAWN_USER);
		}
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
			given(jwtService.issue(eq(user.getPublicId()), eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(accessExp), eq("access")))
				.willReturn("access-token");
			given(jwtService.issue(eq(user.getPublicId()), eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(refreshExp), eq("refresh")))
				.willReturn("refresh-token");

			// when
			Pair<String, String> result = authService.login(email, password);

			// then
			assertThat(result.getFirst()).isEqualTo("access-token");
			assertThat(result.getSecond()).isEqualTo("refresh-token");
			verify(refreshTokenService).save(eq(user.getPublicId()), eq("refresh-token"), anyLong());
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
			verify(jwtService, never()).issue(any(), anyLong(), anyString(), any(), any(), anyString());
			verify(refreshTokenService, never()).save(any(), anyString(), anyLong());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 예외가 발생한다.")
		void 로그인_실패_탈퇴회원() {
			// given
			String email = "user@test.com";
			String password = "plain";
			User user = createUser(1L, email, "encoded");
			user.withdraw();

			given(userRepository.findByEmailOrThrow(email)).willReturn(user);

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.login(email, password));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_WITHDRAWN_USER);
			verify(passwordEncoder, never()).matches(anyString(), anyString());
		}
	}

	@Nested
	@DisplayName("이메일 알림 수신 동의 기본값 테스트")
	class EmailNotificationConsentDefaultTest {

		@Test
		@DisplayName("로컬 회원가입 유저의 이메일 알림 수신 동의 기본값은 false다.")
		void 로컬회원_이메일알림기본값_false() {
			User user = User.createLocalUser("user@test.com", NICKNAME, "encoded", true, true, true, false, false, true);

			assertThat(user.isEmailNotificationAgreed()).isFalse();
		}

		@Test
		@DisplayName("OAuth 회원의 이메일 알림 수신 동의 기본값은 false다.")
		void OAuth회원_이메일알림기본값_false() {
			User user = User.createOAuthUser("user@test.com",
				com.truve.platform.common.constants.AuthProvider.KAKAO,
				"oauth-user-id",
				"oauth-access-token",
				"oauth-refresh-token");

			assertThat(user.isEmailNotificationAgreed()).isFalse();
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

			given(jwtService.parsePublicId(refreshToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));
			given(jwtService.getAccessExpiration()).willReturn(newAccessExp);
			given(jwtService.getRefreshExpiration()).willReturn(newRefreshExp);
			given(jwtService.issue(eq(user.getPublicId()), eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(newAccessExp), eq("access")))
				.willReturn("new-access-token");
			given(jwtService.issue(eq(user.getPublicId()), eq(user.getId()), eq(user.getEmail()), eq(UserRole.MEMBER), eq(newRefreshExp), eq("refresh")))
				.willReturn("new-refresh-token");

			// when
			Pair<String, String> result = authService.reissue(refreshToken);

			// then
			assertThat(result.getFirst()).isEqualTo("new-access-token");
			assertThat(result.getSecond()).isEqualTo("new-refresh-token");
			verify(refreshTokenService).save(eq(user.getPublicId()), eq("new-refresh-token"), anyLong());
		}

		@Test
		@DisplayName("리프레시 토큰 파싱에 실패하면 예외가 발생한다.")
		void 재발급_실패_유효하지_않은_토큰() {
			// given
			String refreshToken = "invalid-refresh-token";
			given(jwtService.parsePublicId(refreshToken)).willThrow(new JwtException("invalid"));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.reissue(refreshToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
			verify(userRepository, never()).findByPublicId(any());
		}

		@Test
		@DisplayName("탈퇴한 회원이면 예외가 발생한다.")
		void 재발급_실패_탈퇴회원() {
			// given
			String refreshToken = "refresh-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.withdraw();

			given(jwtService.parsePublicId(refreshToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.reissue(refreshToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_WITHDRAWN_USER);
		}
	}

	@Nested
	@DisplayName("로그아웃 테스트")
	class LogoutTest {

		@Test
		@DisplayName("액세스 토큰이 유효하고 만료 전이면 블랙리스트에 등록한다.")
		void 로그아웃_성공() {
			// given
			String accessToken = "access-token";
			String jti = "jti-123";
			Date exp = new Date(System.currentTimeMillis() + 60_000L);
			UUID userPublicId = UUID.randomUUID();

			given(jwtService.parsePublicId(accessToken)).willReturn(userPublicId);
			given(jwtService.parseJti(accessToken)).willReturn(jti);
			given(jwtService.parseExpiration(accessToken)).willReturn(exp);

			// when
			authService.logout(accessToken);

			// then
			verify(refreshTokenService).delete(userPublicId);
			verify(accessTokenBlacklistService).save(eq(jti), anyLong());
		}

		@Test
		@DisplayName("액세스 토큰이 이미 만료됐으면 블랙리스트 저장은 생략한다.")
		void 로그아웃_성공_만료된_토큰() {
			// given
			String accessToken = "expired-access-token";
			String jti = "jti-123";
			Date expired = new Date(System.currentTimeMillis() - 1_000L);
			UUID userPublicId = UUID.randomUUID();

			given(jwtService.parsePublicId(accessToken)).willReturn(userPublicId);
			given(jwtService.parseJti(accessToken)).willReturn(jti);
			given(jwtService.parseExpiration(accessToken)).willReturn(expired);

			// when
			authService.logout(accessToken);

			// then
			verify(refreshTokenService).delete(userPublicId);
			verify(accessTokenBlacklistService, never()).save(anyString(), anyLong());
		}

		@Test
		@DisplayName("액세스 토큰 파싱에 실패하면 예외가 발생한다.")
		void 로그아웃_실패_유효하지_않은_토큰() {
			// given
			String accessToken = "invalid-access-token";

			given(jwtService.parsePublicId(accessToken)).willThrow(new JwtException("invalid"));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.logout(accessToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
			verify(refreshTokenService, never()).delete(any());
			verify(accessTokenBlacklistService, never()).save(anyString(), anyLong());
		}
	}

	@Nested
	@DisplayName("회원 탈퇴 테스트")
	class WithdrawTest {

		@Test
		@DisplayName("유효한 액세스 토큰이면 회원 탈퇴를 처리한다.")
		void 회원탈퇴_성공() {
			// given
			String accessToken = "access-token";
			String jti = "jti-123";
			Date exp = new Date(System.currentTimeMillis() + 60_000L);
			User user = createUser(1L, "user@test.com", "encoded");

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));
			given(jwtService.parseJti(accessToken)).willReturn(jti);
			given(jwtService.parseExpiration(accessToken)).willReturn(exp);

			// when
			authService.withdraw(accessToken);

			// then
			assertThat(user.isWithdrawn()).isTrue();
			verify(refreshTokenService).delete(user.getPublicId());
			verify(accessTokenBlacklistService).save(eq(jti), anyLong());
		}

		@Test
		@DisplayName("액세스 토큰 파싱에 실패하면 예외가 발생한다.")
		void 회원탈퇴_실패_유효하지_않은_토큰() {
			// given
			String accessToken = "invalid-access-token";
			given(jwtService.parsePublicId(accessToken)).willThrow(new JwtException("invalid"));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.withdraw(accessToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
			verify(refreshTokenService, never()).delete(any());
		}

		@Test
		@DisplayName("이미 탈퇴한 회원이면 예외가 발생한다.")
		void 회원탈퇴_실패_이미탈퇴회원() {
			// given
			String accessToken = "access-token";
			User user = createUser(1L, "user@test.com", "encoded");
			user.withdraw();

			given(jwtService.parsePublicId(accessToken)).willReturn(user.getPublicId());
			given(userRepository.findByPublicId(user.getPublicId())).willReturn(java.util.Optional.of(user));

			// when
			CustomException exception = assertThrows(CustomException.class, () -> authService.withdraw(accessToken));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_WITHDRAWN_USER);
			verify(refreshTokenService, never()).delete(any());
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
			given(userRepository.findByEmail(email)).willReturn(Optional.empty());
			given(userRepository.existsByNickname(NICKNAME)).willReturn(false);
			given(passwordEncoder.encode(password)).willReturn("encoded");
			given(userRepository.save(any(User.class))).willAnswer(invocation -> {
				User savedUser = invocation.getArgument(0);
				ReflectionTestUtils.setField(savedUser, "id", 1L);
				return savedUser;
			});

			// when
			authService.signUp(email, NICKNAME, password, true, true, true, false, true);

			// then
			ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
			ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
			ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

			verify(userRepository).save(savedUserCaptor.capture());
			verify(emailVerificationRepository).deleteVerifiedEmail(email);
			verify(userSignedUpEventPublisher).publish(keyCaptor.capture(), eventCaptor.capture());

			assertThat(savedUserCaptor.getValue().getEmail()).isEqualTo(email);
			assertThat(savedUserCaptor.getValue().getNickname()).isEqualTo(NICKNAME);
			assertThat(savedUserCaptor.getValue().getPassword()).isEqualTo("encoded");
			assertThat(savedUserCaptor.getValue().getRole()).isEqualTo(UserRole.MEMBER);
			assertThat(savedUserCaptor.getValue().isServiceTermsAgreed()).isTrue();
			assertThat(savedUserCaptor.getValue().isElectronicFinanceTermsAgreed()).isTrue();
			assertThat(savedUserCaptor.getValue().isPrivacyCollectionAgreed()).isTrue();
			assertThat(savedUserCaptor.getValue().isMarketingInfoAgreed()).isFalse();
			assertThat(savedUserCaptor.getValue().isOver14Agreed()).isTrue();
			assertThat(savedUserCaptor.getValue().getPublicId()).isInstanceOf(UUID.class);
			assertThat(keyCaptor.getValue()).isEqualTo(savedUserCaptor.getValue().getPublicId().toString());
			assertThat(eventCaptor.getValue()).isInstanceOf(UserSignedUpEvent.class);
		}

		@Test
		@DisplayName("인증되지 않은 이메일이면 예외가 발생한다.")
		void 회원가입_실패_미인증_이메일() {
			// given
			String email = "new@test.com";
			String password = "plain";
			given(userRepository.findByEmail(email)).willReturn(Optional.empty());
			given(userRepository.existsByNickname(NICKNAME)).willReturn(false);
			given(passwordEncoder.encode(password)).willReturn("encoded");
			given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

			// when
			authService.signUp(email, NICKNAME, password, true, true, true, false, true);

			// then
			verify(userRepository).save(any(User.class));
		}

		@Test
		@DisplayName("이미 가입된 이메일이면 예외가 발생한다.")
		void 회원가입_실패_중복_이메일() {
			// given
			String email = "dup@test.com";
			String password = "plain";
			User existingUser = User.createLocalUser(
				email,
				"existing",
				"encoded-old",
				true,
				true,
				true,
				false,
				false,
				true
			);
			given(userRepository.findByEmail(email)).willReturn(Optional.of(existingUser));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.signUp(email, NICKNAME, password, true, true, true, false, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXISTS_EMAIL);
			verify(userRepository, never()).save(any(User.class));
			verify(emailVerificationRepository, never()).deleteVerifiedEmail(anyString());
			verify(userSignedUpEventPublisher, never()).publish(anyString(), any());
		}

		@Test
		@DisplayName("탈퇴한 유저는 즉시 재가입 처리된다.")
		void 회원가입_성공_탈퇴유저_재가입() {
			// given
			String email = "withdrawn@test.com";
			String password = "plain";
			User withdrawnUser = User.createLocalUser(
				email,
				"oldnick",
				"old-password",
				true,
				true,
				true,
				false,
				false,
				true
			);
			withdrawnUser.withdraw();

			given(userRepository.findByEmail(email)).willReturn(Optional.of(withdrawnUser));
			given(userRepository.existsByNickname(NICKNAME)).willReturn(false);
			given(passwordEncoder.encode(password)).willReturn("encoded");

			// when
			authService.signUp(email, NICKNAME, password, true, true, true, false, true);

			// then
			assertAll(
				() -> assertThat(withdrawnUser.isWithdrawn()).isFalse(),
				() -> assertThat(withdrawnUser.getNickname()).isEqualTo(NICKNAME),
				() -> assertThat(withdrawnUser.getPassword()).isEqualTo("encoded"),
				() -> assertThat(withdrawnUser.isServiceTermsAgreed()).isTrue(),
				() -> assertThat(withdrawnUser.isElectronicFinanceTermsAgreed()).isTrue(),
				() -> assertThat(withdrawnUser.isPrivacyCollectionAgreed()).isTrue(),
				() -> assertThat(withdrawnUser.isOver14Agreed()).isTrue()
			);
			verify(userRepository, never()).save(any(User.class));
			verify(emailVerificationRepository).deleteVerifiedEmail(email);
			verify(userSignedUpEventPublisher, never()).publish(anyString(), any());
		}

		@Test
		@DisplayName("필수 약관에 동의하지 않으면 예외가 발생한다.")
		void 회원가입_실패_필수_약관_미동의() {
			// given
			String email = "new@test.com";
			String password = "plain";

			given(userRepository.findByEmail(email)).willReturn(Optional.empty());

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.signUp(email, NICKNAME, password, true, false, true, false, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
			verify(passwordEncoder, never()).encode(anyString());
			verify(userRepository, never()).save(any(User.class));
			verify(emailVerificationRepository, never()).deleteVerifiedEmail(anyString());
			verify(userSignedUpEventPublisher, never()).publish(anyString(), any());
		}

		@Test
		@DisplayName("닉네임 형식이 유효하지 않으면 예외가 발생한다.")
		void 회원가입_실패_닉네임_형식_오류() {
			// given
			String email = "new@test.com";
			String password = "plain";

			given(userRepository.findByEmail(email)).willReturn(Optional.empty());

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.signUp(email, "a b", password, true, true, true, false, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_NICKNAME);
			verify(userRepository, never()).save(any(User.class));
		}

		@Test
		@DisplayName("영문 16자 닉네임이면 회원가입에 성공한다.")
		void 회원가입_성공_영문16자_닉네임() {
			// given
			String email = "english@test.com";
			String password = "plain";
			String englishNickname = "abcdefghijklmnop";

			given(userRepository.findByEmail(email)).willReturn(Optional.empty());
			given(userRepository.existsByNickname(englishNickname)).willReturn(false);
			given(passwordEncoder.encode(password)).willReturn("encoded");
			given(userRepository.save(any(User.class))).willAnswer(invocation -> {
				User savedUser = invocation.getArgument(0);
				ReflectionTestUtils.setField(savedUser, "id", 2L);
				return savedUser;
			});

			// when
			authService.signUp(email, englishNickname, password, true, true, true, false, true);

			// then
			verify(userRepository).save(any(User.class));
			verify(userSignedUpEventPublisher).publish(anyString(), any());
		}

		@Test
		@DisplayName("이미 사용 중인 닉네임이면 예외가 발생한다.")
		void 회원가입_실패_중복_닉네임() {
			// given
			String email = "new@test.com";
			String password = "plain";

			given(userRepository.findByEmail(email)).willReturn(Optional.empty());
			given(userRepository.existsByNickname(NICKNAME)).willReturn(true);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.signUp(email, NICKNAME, password, true, true, true, false, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXISTS_NICKNAME);
			verify(userRepository, never()).save(any(User.class));
		}
	}
}
