package com.truve.platform.musical.service.domain.constant;

import java.util.HashMap;
import java.util.Map;

public enum SeatGrade {
	VIP("VIP", 0),
	R("R", 1),
	S("S", 2),
	A("A", 3),
	OP("OP", 4);

	private static final Map<String, SeatGrade> BY_LABEL = new HashMap<>();

	static {
		for (SeatGrade grade : values()) {
			BY_LABEL.put(grade.label, grade);
		}
	}

	private final String label;
	private final int order;

	SeatGrade(String label, int order) {
		this.label = label;
		this.order = order;
	}

	public static SeatGrade fromLabel(String label) {
		SeatGrade grade = BY_LABEL.get(label);
		if (grade == null) {
			throw new IllegalArgumentException("Unknown seat grade: " + label);
		}
		return grade;
	}

	public String getLabel() {
		return label;
	}

	public int getOrder() {
		return order;
	}
}
