package com.truve.platform.auth.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.util.Pair;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.auth.service.domain.dto.request.AuthRequest;
import com.truve.platform.auth.service.domain.entity.User;
import com.truve.platform.auth.service.repository.EmailVerificationRepository;
import com.truve.platform.auth.service.repository.SocialRegistrationRepository;
import com.truve.platform.auth.service.repository.UserRepository;
import com.truve.platform.auth.service.security.JwtService;
import com.truve.platform.auth.service.service.social.OAuthProviderClient;
import com.truve.platform.auth.service.service.social.SocialRegistrationInfo;
import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.constants.UserRole;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class SocialLoginServiceTest {

	@Mock
	private OAuthProviderClient naverOAuthProviderClient;
	@Mock
	private List<OAuthProviderClient> oauthProviderClients;
	@Mock
	private UserRepository userRepository;
	@Mock
	private EmailVerificationRepository emailVerificationRepository;
	@Mock
	private JwtService jwtService;
	@Mock
	private RefreshTokenService refreshTokenService;
	@Mock
	private SocialRegistrationRepository socialRegistrationRepository;
	@Mock
	private UserSignedUpEventPublisher userSignedUpEventPublisher;

	@InjectMocks
	private SocialLoginService socialLoginService;

	@Test
	@DisplayName("소셜 가입 완료에 성공하면 회원을 저장하고 로그인 토큰을 발급한다.")
	void 소셜회원가입완료_성공() {
		// given
		String registrationToken = "registration-token";
		String email = "social@test.com";
		String nickname = "tester";
		AuthRequest.CompleteSocialSignUp request = new AuthRequest.CompleteSocialSignUp(
			registrationToken,
			email,
			nickname,
			true,
			true,
			true,
			false,
			true
		);
		SocialRegistrationInfo registrationInfo = new SocialRegistrationInfo(
			AuthProvider.KAKAO,
			"provider-user-id",
			email,
			"oauth-access-token",
			"oauth-refresh-token"
		);
		Date accessExp = new Date(System.currentTimeMillis() + 60_000L);
		Date refreshExp = new Date(System.currentTimeMillis() + 120_000L);

		given(socialRegistrationRepository.find(registrationToken)).willReturn(registrationInfo);
		given(emailVerificationRepository.isVerifiedEmail(email)).willReturn("verified");
		given(userRepository.existsByEmail(email)).willReturn(false);
		given(userRepository.existsByNickname(nickname)).willReturn(false);
		given(userRepository.save(any(User.class))).willAnswer(invocation -> {
			User user = invocation.getArgument(0);
			ReflectionTestUtils.setField(user, "id", 1L);
			return user;
		});
		given(jwtService.getAccessExpiration()).willReturn(accessExp);
		given(jwtService.getRefreshExpiration()).willReturn(refreshExp);
		given(jwtService.issue(any(UUID.class), eq(1L), eq(email), eq(UserRole.MEMBER), eq(accessExp), eq("access")))
			.willReturn("access-token");
		given(jwtService.issue(any(UUID.class), eq(1L), eq(email), eq(UserRole.MEMBER), eq(refreshExp), eq("refresh")))
			.willReturn("refresh-token");

		// when
		Pair<String, String> result = socialLoginService.completeSignUp(request);

		// then
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());

		User savedUser = userCaptor.getValue();
		assertAll(
			() -> assertThat(result.getFirst()).isEqualTo("access-token"),
			() -> assertThat(result.getSecond()).isEqualTo("refresh-token"),
			() -> assertThat(savedUser.getEmail()).isEqualTo(email),
			() -> assertThat(savedUser.getNickname()).isEqualTo(nickname),
			() -> assertThat(savedUser.getProvider()).isEqualTo(AuthProvider.KAKAO),
			() -> assertThat(savedUser.getOAuthUserId()).isEqualTo("provider-user-id"),
			() -> assertThat(savedUser.isServiceTermsAgreed()).isTrue(),
			() -> assertThat(savedUser.isElectronicFinanceTermsAgreed()).isTrue(),
			() -> assertThat(savedUser.isPrivacyCollectionAgreed()).isTrue(),
			() -> assertThat(savedUser.isMarketingInfoAgreed()).isFalse(),
			() -> assertThat(savedUser.isOver14Agreed()).isTrue()
		);
		verify(emailVerificationRepository).deleteVerifiedEmail(email);
		verify(socialRegistrationRepository).delete(registrationToken);
		verify(refreshTokenService).save(any(UUID.class), eq("refresh-token"), any(Long.class));
		verify(userSignedUpEventPublisher).publish(anyString(), any());
	}

	@Test
	@DisplayName("소셜 가입 토큰이 없으면 예외가 발생한다.")
	void 소셜회원가입완료_실패_토큰없음() {
		// given
		given(socialRegistrationRepository.find("registration-token")).willReturn(null);

		// when
		CustomException exception = assertThrows(
			CustomException.class,
			() -> socialLoginService.completeSignUp(
				new AuthRequest.CompleteSocialSignUp(
					"registration-token",
					"social@test.com",
					"tester",
					true,
					true,
					true,
					false,
					true
				)
			)
		);

		// then
		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SOCIAL_REGISTRATION_TOKEN);
		verify(userRepository, never()).save(any());
	}

	@Test
	@DisplayName("이메일 인증이 안 되어 있으면 예외가 발생한다.")
	void 소셜회원가입완료_실패_이메일미인증() {
		// given
		String registrationToken = "registration-token";
		String email = "social@test.com";
		given(socialRegistrationRepository.find(registrationToken)).willReturn(
			new SocialRegistrationInfo(AuthProvider.NAVER, "provider-user-id", email, "oauth-access-token", "oauth-refresh-token")
		);
		given(emailVerificationRepository.isVerifiedEmail(email)).willReturn(null);

		// when
		CustomException exception = assertThrows(
			CustomException.class,
			() -> socialLoginService.completeSignUp(
				new AuthRequest.CompleteSocialSignUp(
					registrationToken,
					email,
					"tester",
					true,
					true,
					true,
					false,
					true
				)
			)
		);

		// then
		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_VERIFIED_EMAIL);
		verify(userRepository, never()).save(any());
	}
}
