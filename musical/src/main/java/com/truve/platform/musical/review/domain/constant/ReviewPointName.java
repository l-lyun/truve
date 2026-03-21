package com.truve.platform.musical.review.domain.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ReviewPointName {

	STAGE_PRODUCTION(ReviewPointCategory.CHARM, "무대연출", "C01", 1L),
	STORY(ReviewPointCategory.CHARM, "스토리", "C02", 2L),
	ACTING(ReviewPointCategory.CHARM, "배우연기", "C03", 3L),
	DANCE(ReviewPointCategory.CHARM, "안무", "C04", 4L),
	NUMBER(ReviewPointCategory.CHARM, "넘버", "C05", 5L),

	IMMERSION(ReviewPointCategory.EMOTION, "몰입감", "E01", 1L),
	TENSION(ReviewPointCategory.EMOTION, "텐션", "E02", 2L),
	ENJOYMENT(ReviewPointCategory.EMOTION, "즐거움", "E03", 3L),
	CATHARSIS(ReviewPointCategory.EMOTION, "카타르시스", "E04", 4L),
	TOUCHING(ReviewPointCategory.EMOTION, "감동", "E05", 5L),
	;

	private final ReviewPointCategory category;
	private final String label;
	private final String code;
	private final Long order;

	public boolean isCharmPoint() {
		return this.category == ReviewPointCategory.CHARM;
	}

	public boolean isEmotionPoint() {
		return this.category == ReviewPointCategory.EMOTION;
	}
}
