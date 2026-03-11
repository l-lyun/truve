package com.truve.platform.musical.review.domain.entity;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.musical.review.domain.constant.ReviewPointCategory;

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
@Table(name = "review_point_type")
public class ReviewPointType extends BaseEntity {

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ReviewPointCategory category;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private Long order;

	@Builder
	public ReviewPointType(ReviewPointCategory category, String name, String code, Long order) {
		this.category = category;
		this.name = name;
		this.code = code;
		this.order = order;
	}
}
