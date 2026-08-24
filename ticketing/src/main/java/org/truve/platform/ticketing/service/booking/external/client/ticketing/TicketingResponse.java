package org.truve.platform.ticketing.service.booking.external.client.ticketing;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingResponse {

	public interface FlatSeatInfo {
		Long getShowId();

		Long getShowScheduleId();

		String getShowTitle();

		String getVenueName();

		LocalDateTime getStartAt();

		String getPosterImg();

		String getSectionName();

		Long getFloor();

		String getGradeName();

		String getSeatRow();

		Long getSeatNumber();

		Long getPrice();

		Long getScheduledSeatId();
	}

	@Getter
	@AllArgsConstructor
	public static class SeatInfo {
		private final Long showId;
		private final Long showScheduleId;
		private final String showTitle;
		private final String venueName;
		private final LocalDateTime startAt;
		private final String posterImg;
		private final List<Seat> seats;

		public static SeatInfo from(FlatSeatInfo flat, List<Seat> seats) {
			return new SeatInfo(
				flat.getShowId(),
				flat.getShowScheduleId(),
				flat.getShowTitle(),
				flat.getVenueName(),
				flat.getStartAt(),
				flat.getPosterImg(),
				seats
			);
		}
	}

	@Getter
		@AllArgsConstructor
		public static class Seat {
			private final Long scheduledSeatId;
			private final String sectionName;
			private final Long floor;
			private final String gradeName;
			private final String seatRow;
			private final Long seatNumber;
		private final Long price;

			public static Seat from(FlatSeatInfo flat) {
				return new Seat(
					flat.getScheduledSeatId(),
					flat.getSectionName(),
					flat.getFloor(),
					flat.getGradeName(),
					flat.getSeatRow(),
				flat.getSeatNumber(),
				flat.getPrice()
			);
		}
	}
}
