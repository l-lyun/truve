package com.truve.platform.musical.user.domain.entity;

import java.util.UUID;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user")
public class User extends BaseEntity {

	@Column(nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false)
	private String nickname;

	@Builder
	public User(UUID userId, String nickname) {
		this.userId = userId;
		this.nickname = nickname;
	}
}
