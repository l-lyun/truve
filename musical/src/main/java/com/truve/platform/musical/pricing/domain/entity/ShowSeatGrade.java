package com.truve.platform.musical.pricing.domain.entity;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.musical.show.domain.entity.Show;

import jakarta.persistence.AttributeOverride;
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
@Table(name = "show_seat_grade")
@AttributeOverride(name = "id", column = @Column(name = "show_seat_grade_id"))
public class ShowSeatGrade extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "show_id", nullable = false)
	private Show show;

	@Column(nullable = false, length = 50)
	private String gradeName;

	@Column(nullable = false)
	private Integer basePrice;

	@Column(length = 20)
	private String colorCode;

	@Builder
	private ShowSeatGrade(Show show, String gradeName, Integer basePrice, String colorCode) {
		this.show = show;
		this.gradeName = gradeName;
		this.basePrice = basePrice;
		this.colorCode = colorCode;
	}
}
