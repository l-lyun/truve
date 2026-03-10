package org.truve.platform.ticketing.service.ticket.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketRequest {

	@Getter
	@AllArgsConstructor
	public static class Create {
		@NotEmpty
		private List<Long> seatIds;
	}
}
