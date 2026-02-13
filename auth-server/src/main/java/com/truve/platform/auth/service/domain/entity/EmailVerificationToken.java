package com.truve.platform.auth.service.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// TODO: DB -> 레디스로 전환

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerificationToken extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String email;
	private String code;
	private boolean isVerified;

	private EmailVerificationToken(String email, String code) {
		this.email = email;
		this.code = code;
		isVerified = false;
	}

	public static EmailVerificationToken create(String email, String code) {
		return new EmailVerificationToken(email, code);
	}

	public void verifyEmail() {
		this.isVerified = true;
	}
}
