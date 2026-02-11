package com.truve.platform.user.service.domain.entity;

import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.constants.UserRole;
import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

	@Email
	@Column(nullable = false, unique = true)
	private String email;

	// TODO: 기획 논의 이후 비밀번호 정책 정규식 설정
	private String password;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private AuthProvider provider;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private UserRole role;

	private String oAuthUserId;

	private String oAuthAccessToken;

	private String oAuthRefreshToken;


	@Builder
	private User(String email, String password, AuthProvider provider,
		UserRole role,  String oAuthUserId, String oAuthAccessToken, String oAuthRefreshToken) {
		this.email = email;
		this.password = password;
		this.provider = provider;
		this.role = role;
		this.oAuthUserId = oAuthUserId;
		this.oAuthAccessToken = oAuthAccessToken;
		this.oAuthRefreshToken = oAuthRefreshToken;
	}

	public static User createLocalUser(String email, String password) {
		return User.builder()
			.email(email)
			.password(password)
			.provider(AuthProvider.LOCAL)
			.role(UserRole.MEMBER)
			.build();
	}

	public static User createOAuthUser(
		String email,
		AuthProvider provider,
		String oAuthUserId,
		String oAuthAccessToken,
		String oAuthRefreshToken
	) {
		return User.builder()
			.email(email)
			.provider(provider)
			.role(UserRole.MEMBER)
			.oAuthUserId(oAuthUserId)
			.oAuthAccessToken(oAuthAccessToken)
			.oAuthRefreshToken(oAuthRefreshToken)
			.build();
	}
}
