package org.truve.platform.ticketing.service.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingResponse {

	@Getter
	@AllArgsConstructor
	public static class Enter {
		String sessionToken;
		long expireIn;
	}
}
