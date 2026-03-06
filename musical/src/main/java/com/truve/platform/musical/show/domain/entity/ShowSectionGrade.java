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
@Table(name = "show_section_grade")
public class ShowSectionGrade extends BaseEntity {


	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "show_id")
	private Show show;

	@Column(nullable = false, length = 50)
	private String gradeName;

	@Column(length = 20)
	private String colorCode;

	@Column(nullable = false)
	private Long price;

	@Builder
	public ShowSectionGrade(Show show, String gradeName, String colorCode, Long price) {
		this.show = show;
		this.gradeName = gradeName;
		this.colorCode = colorCode;
		this.price = price;
	}
}
