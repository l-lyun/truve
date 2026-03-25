package org.truve.platform.ticketing.service.ticketing.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingRequest {

	@Getter
	@AllArgsConstructor
	public static class HoldSeat {
		@NotEmpty
		List<Long> scheduledSeatIds;
	}

	@Getter
	@AllArgsConstructor
	public static class DeleteHoldSeat {
		@NotEmpty
		List<Long> scheduledSeatIds;
	}
}
