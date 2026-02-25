package org.truve.platform.ticketing.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SessionTicketValueDTO {
	String userId;
	String showId;

	public static SessionTicketValueDTO of(String userId, String showId) {
		return new SessionTicketValueDTO(userId, showId);
	}
}
