package org.truve.platform.ticketing.service.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketResponse {

	@Getter
	@AllArgsConstructor
	public static class Create {
		private final String reservationNumber;
	}
}