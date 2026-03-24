package com.truve.platform.auth.service.service;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.util.Pair;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.auth.service.event.UserSignedUpEvent;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.auth.service.domain.entity.User;
import com.truve.platform.auth.service.repository.EmailVerificationRepository;
import com.truve.platform.auth.service.repository.UserRepository;
import com.truve.platform.auth.service.security.JwtService;
import com.truve.platform.auth.service.security.TokenType;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
	private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9가-힣]{2,10}$");

	private final UserRepository userRepository;
	private final EmailVerificationRepository emailVerificationRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final AccessTokenBlacklistService accessTokenBlacklistService;
	private final UserSignedUpEventPublisher  userSignedUpEventPublisher;

	@Transactional
	public Pair<String, String> login(String email, String password) {
		User user = userRepository.findByEmailOrThrow(email);

		Preconditions.validate(passwordEncoder.matches(password, user.getPassword()), ErrorCode.NOT_CORRECT_PASSWORD);

		var accessExp = jwtService.getAccessExpiration();
		var refreshExp = jwtService.getRefreshExpiration();

		String accessToken = jwtService.issue(
			user.getPublicId(), user.getId(), user.getEmail(), user.getRole(), accessExp, TokenType.ACCESS_TOKEN.getType()
		);

		String refreshToken = jwtService.issue(user.getPublicId(), user.getId(), user.getEmail(), user.getRole(), refreshExp,
			TokenType.REFRESH_TOKEN.getType());

		long refreshTtlMs = refreshExp.getTime() - System.currentTimeMillis();
		refreshTokenService.save(user.getPublicId(), refreshToken, refreshTtlMs);

		return Pair.of(accessToken, refreshToken);
	}

	@Transactional
	public Pair<String, String> reissue(String refreshToken) {
		UUID userPublicId;

		try {
			userPublicId = jwtService.parsePublicId(refreshToken);
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		User user = userRepository.findByPublicId(userPublicId)
			.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REFRESH_TOKEN));
		var newAccessExp = jwtService.getAccessExpiration();
		var newRefreshExp = jwtService.getRefreshExpiration();

		String newAccessToken = jwtService.issue(user.getPublicId(), user.getId(), user.getEmail(), user.getRole(), newAccessExp,
			TokenType.ACCESS_TOKEN.getType());

		String newRefreshToken = jwtService.issue(user.getPublicId(), user.getId(), user.getEmail(), user.getRole(), newRefreshExp,
			TokenType.REFRESH_TOKEN.getType());

		long newRefreshTtlMs = newRefreshExp.getTime() - System.currentTimeMillis();
		refreshTokenService.save(user.getPublicId(), newRefreshToken, newRefreshTtlMs);

		return Pair.of(newAccessToken, newRefreshToken);
	}

	@Transactional
	public void logout(String accessToken) {
		try {
			UUID userPublicId = jwtService.parsePublicId(accessToken);
			String jti = jwtService.parseJti(accessToken);
			var exp = jwtService.parseExpiration(accessToken);
			refreshTokenService.delete(userPublicId);

			long ttlMs = exp.getTime() - System.currentTimeMillis();
			if (ttlMs > 0) {
				accessTokenBlacklistService.save(jti, ttlMs);
			}
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
	}

	@Transactional
	public void signUp(
		String email,
		String nickname,
		String password,
		boolean serviceTermsAgreed,
		boolean electronicFinanceTermsAgreed,
		boolean privacyCollectionAgreed,
		boolean marketingInfoAgreed,
		boolean over14Agreed
	) {

		String verifiedAt = emailVerificationRepository.isVerifiedEmail(email);
		Preconditions.validate(!(verifiedAt == null || verifiedAt.isBlank()), ErrorCode.NOT_VERIFIED_EMAIL);

		Preconditions.validate(
			!userRepository.existsByEmail(email),
			ErrorCode.ALREADY_EXISTS_EMAIL
		);
		Preconditions.validate(
			nickname != null && NICKNAME_PATTERN.matcher(nickname).matches(),
			ErrorCode.INVALID_NICKNAME
		);
		Preconditions.validate(
			!userRepository.existsByNickname(nickname),
			ErrorCode.ALREADY_EXISTS_NICKNAME
		);
		Preconditions.validate(
			serviceTermsAgreed && electronicFinanceTermsAgreed && privacyCollectionAgreed && over14Agreed,
			ErrorCode.REQUIRED_TERMS_NOT_AGREED
		);

		String encodedPassword = passwordEncoder.encode(password);

		User user = User.createLocalUser(
			email,
			nickname,
			encodedPassword,
			serviceTermsAgreed,
			electronicFinanceTermsAgreed,
			privacyCollectionAgreed,
			marketingInfoAgreed,
			over14Agreed
		);

		userRepository.save(user);
		emailVerificationRepository.deleteVerifiedEmail(email);

		UserSignedUpEvent event = UserSignedUpEvent.from(user);
		userSignedUpEventPublisher.publish(user.getPublicId().toString(), event);
	}

}
