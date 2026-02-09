package com.truve.platform.user.service.domain.entity;

import com.truve.platform.common.constants.AuthProvider;
import com.truve.platform.common.constants.UserRole;
import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String email;

	// TODO: 기획 논의 이후 비밀번호 정책 정규식 설정
	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private AuthProvider provider;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private UserRole role;

	@Builder
	public User(String email, String password, AuthProvider provider, UserRole role) {
		this.email = email;
		this.password = password;
		this.provider = provider;
		this.role = role;
	}

}
