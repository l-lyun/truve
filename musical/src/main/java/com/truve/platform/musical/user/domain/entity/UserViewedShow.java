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
@Table(name = "user_viewed_show")
public class UserViewedShow extends BaseEntity {

	private UUID userId;

	@Column(nullable = false)
	private Long showId;

	@Builder
	public UserViewedShow(UUID userId, Long showId) {
		this.userId = userId;
		this.showId = showId;
	}
}
