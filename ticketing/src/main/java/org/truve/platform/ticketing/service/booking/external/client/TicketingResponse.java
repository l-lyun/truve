package org.truve.platform.ticketing.service.booking.external.client;

import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingResponse {

	@Getter
	@AllArgsConstructor
	public static class SeatInfo {
		private final String sectionName;
		private final Long floor;
		private final String gradeName;
		private final String seatRow;
		private final Long seatNumber;
		private final Long price;

		public static SeatInfo from(Seat seat) {
			return new SeatInfo(
				seat.getSeatSection().getName(),
				seat.getSeatSection().getFloor(),
				seat.getSeatSection().getGradeName(),
				seat.getSeatRow(),
				seat.getSeatNumber(),
				seat.getSeatSection().getPrice()
			);
		}
	}
}
