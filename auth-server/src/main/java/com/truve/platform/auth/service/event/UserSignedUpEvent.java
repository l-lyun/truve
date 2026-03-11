package com.truve.platform.auth.service.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.truve.platform.auth.service.domain.entity.User;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserSignedUpEvent {

	private static final String EVENT_TYPE = "USER_SIGNED_UP";

	private UUID eventId;
	private String eventType;
	private LocalDateTime occurredAt;
	private UUID userId;
	private String nickname;

	public static UserSignedUpEvent from (User user) {
		return new UserSignedUpEvent(
			UUID.randomUUID(),
			EVENT_TYPE,
			LocalDateTime.now(),
			user.getPublicId(),
			// TODO: 닉네임으로 변경 예정
			user.getEmail()
		);
	}
}
