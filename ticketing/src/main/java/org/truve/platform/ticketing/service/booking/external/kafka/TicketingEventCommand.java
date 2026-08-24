package org.truve.platform.ticketing.service.booking.external.kafka;

import java.util.List;
import java.util.UUID;

import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TicketingEventCommand {

	@JsonIgnoreProperties(value = {"eventType"})
	public interface TicketingEvent {
		String getReservationNumber();

		String getEventType();
	}

	@Getter
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	public static class HoldReleased implements TicketingEvent {
		private String reservationNumber;
		private UUID userId;
		private List<Long> scheduledSeatIds;

		public static HoldReleased of(Reservation reservation, List<Long> scheduledSeatIds) {
			return HoldReleased.builder()
				.reservationNumber(reservation.getNumber())
				.userId(reservation.getUserId())
				.scheduledSeatIds(List.copyOf(scheduledSeatIds))
				.build();
		}

		@Override
		public String getEventType() {
			return "HOLD_RELEASED";
		}
	}

	@Getter
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SoldConfirmed implements TicketingEvent {
		private String reservationNumber;
		private UUID userId;
		private List<Long> scheduledSeatIds;

		public static SoldConfirmed of(Reservation reservation, List<Long> scheduledSeatIds) {
			return SoldConfirmed.builder()
				.reservationNumber(reservation.getNumber())
				.userId(reservation.getUserId())
				.scheduledSeatIds(List.copyOf(scheduledSeatIds))
				.build();
		}

		@Override
		public String getEventType() {
			return "SOLD_CONFIRMED";
		}
	}
}
