package org.truve.platform.ticketing.service.ticketing.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SessionTicketValueDTO {
	private Long userId;
	private Long showId;

	public static SessionTicketValueDTO of(Long userId, Long showId) {
		return new SessionTicketValueDTO(userId, showId);
	}
}
