package com.truve.platform.musical.show.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "home_banner")
public class HomeBanner extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "show_id", nullable = false)
	private Show show;

	@Column(name = "show_id", nullable = false, insertable = false, updatable = false)
	private Long showId;

	@Column(name = "image_key", nullable = false, length = 500)
	private String imageKey;

	@Column(name = "display_order", nullable = false)
	private Integer displayOrder;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Builder
	private HomeBanner(Show show, String imageKey, Integer displayOrder, boolean isActive) {
		this.show = show;
		this.imageKey = imageKey;
		this.displayOrder = displayOrder;
		this.isActive = isActive;
	}
}
