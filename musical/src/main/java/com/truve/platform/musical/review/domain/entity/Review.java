package com.truve.platform.musical.review.domain.entity;

import java.time.LocalDateTime;
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
@Table(name = "review")
public class Review extends BaseEntity {

	@Column(nullable = false)
	private Long showId;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private String content;

	@Column(nullable = false)
	private Boolean isPositive;

	@Column(nullable = false)
	private LocalDateTime watchedAt;

	@Column
	private LocalDateTime deletedAt;

	@Builder
	public Review(Long showId, UUID userId, String content, Boolean isPositive, LocalDateTime watchedAt) {
		this.showId = showId;
		this.userId = userId;
		this.content = content;
		this.isPositive = isPositive;
		this.watchedAt = watchedAt;
	}
}
