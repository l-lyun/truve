package com.truve.platform.musical.show.domain.constant;

public enum HomeRegion {
	ALL(null),
	SEOUL("서울"),
	GYEONGGI("경기"),
	GANGWON("강원"),
	CHUNGCHEONG("충청"),
	JEOLLA("전라"),
	GYEONGSANG("경상"),
	JEJU("제주");

	private final String keyword;

	HomeRegion(String keyword) {
		this.keyword = keyword;
	}

	public String getKeyword() {
		return keyword;
	}
}
