package com.truve.platform.musical.service.domain.constant;

import java.util.HashMap;
import java.util.Map;

public enum ActorRole {
	LEAD("주연", 0),
	SUPPORTING("조연", 1);

	private static final Map<String, ActorRole> BY_LABEL = new HashMap<>();

	static {
		for (ActorRole role : values()) {
			BY_LABEL.put(role.label, role);
		}
	}

	private final String label;
	private final int order;

	ActorRole(String label, int order) {
		this.label = label;
		this.order = order;
	}

	public static ActorRole fromLabel(String label) {
		ActorRole role = BY_LABEL.get(label);
		if (role == null) {
			throw new IllegalArgumentException("Unknown actor role: " + label);
		}
		return role;
	}

	public String getLabel() {
		return label;
	}

	public int getOrder() {
		return order;
	}
}
