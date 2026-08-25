package org.truve.platform.ticketing.service.ticketing.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingRequest {

	@Getter
	@AllArgsConstructor
	public static class HoldSeat {
		@NotEmpty
		@Size(max = 4)
		List<@NotNull Long> scheduledSeatIds;
	}

	@Getter
	@AllArgsConstructor
	public static class DeleteHoldSeat {
		@NotEmpty
		@Size(max = 4)
		List<@NotNull Long> scheduledSeatIds;
	}
}
