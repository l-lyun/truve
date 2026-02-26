package com.truve.platform.auth.service.repository;

import java.time.Duration;
import org.springframework.stereotype.Repository;
import com.truve.platform.auth.service.support.RedisSupport;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepository {

	private static final String VERIFY_EMAIL_PREFIX = "email:";
	private static final String VERIFIED_EMAIL_PREFIX = "email:verified:";

	private final RedisSupport redisSupport;

	public void registerEmailVerificationCode(String email, String verificationCode) {
		String key = VERIFY_EMAIL_PREFIX + email;
		redisSupport.setValueWithTtl(
			key, verificationCode,
			Duration.ofMinutes(10)
		);
	}

	public boolean verifyEmailVerificationCode(String email, String verificationCode) {
		String key =  VERIFY_EMAIL_PREFIX + email;
		String savedVerificationCode = redisSupport.getValue(key);

		if (savedVerificationCode == null || savedVerificationCode.isBlank()) {
			return false;
		}

		return savedVerificationCode.equals(verificationCode);
	}

	public void registerVerifiedEmail(String email) {
		String key = VERIFIED_EMAIL_PREFIX + email;
		String verifiedAt = String.valueOf(System.currentTimeMillis());
		redisSupport.setValueWithTtl(key, verifiedAt, Duration.ofMinutes(30));
	}

	public String isVerifiedEmail(String email) {
		String key = VERIFIED_EMAIL_PREFIX + email;
		return redisSupport.getValue(key);
	}

	public void deleteVerifiedEmail(String email) {
		String key = VERIFIED_EMAIL_PREFIX + email;
		redisSupport.delete(key);
	}

	public void  deleteEmailVerificationCode(String email) {
		String key = VERIFY_EMAIL_PREFIX + email;
		redisSupport.delete(key);
	}
}
