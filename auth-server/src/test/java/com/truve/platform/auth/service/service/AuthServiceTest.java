package com.truve.platform.auth.service.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Date;
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
		User user = User.createLocalUser(email, NICKNAME, encodedPassword, true, true, true, false, true);
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

			given(emailVerificationRepository.isVerifiedEmail(email)).willReturn("");

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> authService.signUp(email, NICKNAME, password, true, true, true, false, true)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_VERIFIED_EMAIL);
			verify(userRepository, never()).save(any(User.class));
			verify(userSignedUpEventPublisher, never()).publish(anyString(), any());
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
		@DisplayName("필수 약관에 동의하지 않으면 예외가 발생한다.")
		void 회원가입_실패_필수_약관_미동의() {
			// given
			String email = "new@test.com";
			String password = "plain";

			given(emailVerificationRepository.isVerifiedEmail(email)).willReturn("1700000000000");
			given(userRepository.existsByEmail(email)).willReturn(false);
			given(userRepository.existsByNickname(NICKNAME)).willReturn(false);

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

			given(emailVerificationRepository.isVerifiedEmail(email)).willReturn("1700000000000");
			given(userRepository.existsByEmail(email)).willReturn(false);

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
		@DisplayName("이미 사용 중인 닉네임이면 예외가 발생한다.")
		void 회원가입_실패_중복_닉네임() {
			// given
			String email = "new@test.com";
			String password = "plain";

			given(emailVerificationRepository.isVerifiedEmail(email)).willReturn("1700000000000");
			given(userRepository.existsByEmail(email)).willReturn(false);
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
