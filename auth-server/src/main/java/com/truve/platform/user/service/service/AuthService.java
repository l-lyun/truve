package com.truve.platform.user.service.service;

import org.springframework.data.util.Pair;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.constants.UserRole;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.user.service.domain.entity.User;
import com.truve.platform.user.service.repository.EmailVerificationRepository;
import com.truve.platform.user.service.repository.UserRepository;
import com.truve.platform.user.service.security.JwtService;
import com.truve.platform.user.service.security.TokenType;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final EmailVerificationRepository emailVerificationRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final AccessTokenBlacklistService accessTokenBlacklistService;

	@Transactional
	public Pair<String, String> login(String email, String password) {
		User user = userRepository.findByEmailOrThrow(email);

		Preconditions.validate(passwordEncoder.matches(password, user.getPassword()), ErrorCode.NOT_CORRECT_PASSWORD);

		var accessExp = jwtService.getAccessExpiration();
		var refreshExp = jwtService.getRefreshExpiration();

		String accessToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), accessExp, TokenType.ACCESS_TOKEN.getType());

		String refreshToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), refreshExp,
			TokenType.REFRESH_TOKEN.getType());

		long refreshTtlMs = refreshExp.getTime() - System.currentTimeMillis();
		refreshTokenService.save(user.getId(), refreshToken, refreshTtlMs);

		return Pair.of(accessToken, refreshToken);
	}

	@Transactional
	public Pair<String, String> reissue(String refreshToken) {
		String email;

		try {
			email = jwtService.parseEmail(refreshToken);
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		User user = userRepository.findByEmailOrThrow(email);
		var newAccessExp = jwtService.getAccessExpiration();
		var newRefreshExp = jwtService.getRefreshExpiration();

		String newAccessToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), newAccessExp,
			TokenType.ACCESS_TOKEN.getType());

		String newRefreshToken = jwtService.issue(user.getId(), user.getEmail(), user.getRole(), newRefreshExp,
			TokenType.REFRESH_TOKEN.getType());

		long newRefreshTtlMs = newRefreshExp.getTime() - System.currentTimeMillis();
		refreshTokenService.save(user.getId(), newRefreshToken, newRefreshTtlMs);

		return Pair.of(newAccessToken, newRefreshToken);
	}

	@Transactional
	public void logout(Long id, String accessToken) {
		refreshTokenService.delete(id);

		try {
			String jti = jwtService.parseJti(accessToken);
			var exp = jwtService.parseExpiration(accessToken);

			long ttlMs = exp.getTime() - System.currentTimeMillis();
			if (ttlMs > 0) {
				accessTokenBlacklistService.save(jti, ttlMs);
			}
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
		}
	}

	@Transactional
	public void signUp(String email, String password) {

		Preconditions.validate(
			emailVerificationRepository.existsByEmailAndIsVerifiedTrue(email),
			ErrorCode.NOT_VERIFIED_EMAIL
		);

		Preconditions.validate(
			!userRepository.existsByEmail(email),
			ErrorCode.ALREADY_EXISTS_EMAIL
		);

		String encodedPassword = passwordEncoder.encode(password);

		// TODO: OAuth 및 Role 세분화 시 동적으로 변경 필요
		User user = User.builder()
			.email(email)
			.password(encodedPassword)
			.provider(AuthProvider.LOCAL)
			.role(UserRole.MEMBER)
			.build();
		userRepository.save(user);
		emailVerificationRepository.deleteByEmail(email);
	}

}
