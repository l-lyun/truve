package org.truve.platform.ticketing.service.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class BookingResponse {

	@Getter
	@AllArgsConstructor
	public static class Create {
		private final String reservationNumber;
	}
}