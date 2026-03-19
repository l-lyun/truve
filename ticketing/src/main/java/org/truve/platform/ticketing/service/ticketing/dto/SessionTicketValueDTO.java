package org.truve.platform.ticketing.service.ticketing.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SessionTicketValueDTO {
	private UUID userId;
	private Long showId;

	public static SessionTicketValueDTO of(UUID userId, Long showId) {
		return new SessionTicketValueDTO(userId, showId);
	}
}
