package com.truve.platform.musical.review.domain.constant;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public enum ReviewPointCategory {

	CHARM("매력 포인트"),
	EMOTION("감정 포인트"),
	;

	private final String category;
}
