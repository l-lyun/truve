package com.truve.platform.musical.review.domain.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ReviewSortType {
	LATEST("최신순"),
	POSITIVE("좋았어요"),
	NEGATIVE("별로예요")
	,;

	private final String description;
}
