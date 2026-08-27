package org.truve.platform.ticketing.service.booking.external.kafka;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class TicketingEventCommand {

	@JsonIgnoreProperties(value = {"eventType"})
	public interface TicketingEvent {
		String getReservationNumber();

		String getEventType();

		@JsonIgnore
		default String getMessageKey() {
			return getReservationNumber();
		}
	}

	@Getter
	@Builder(access = AccessLevel.PRIVATE)
	@AllArgsConstructor
	@NoArgsConstructor
	public static class HoldRequested implements TicketingEvent {
		private String holdId;
		private String reservationNumber;
		private UUID userId;
		private String sessionToken;
		private Long showScheduleId;
		private List<Long> scheduledSeatIds;
		@JsonFormat(shape = JsonFormat.Shape.ARRAY)
		private LocalDateTime expiresAt;

		public static HoldRequested of(
			String holdId,
			String reservationNumber,
			UUID userId,
			String sessionToken,
			Long showScheduleId,
			List<Long> scheduledSeatIds,
			LocalDateTime expiresAt
		) {
			if (holdId == null || holdId.isBlank()
				|| reservationNumber == null || reservationNumber.isBlank()
				|| sessionToken == null || sessionToken.isBlank()) {
				throw new IllegalArgumentException("hold event identifiers must not be blank");
			}
			return HoldRequested.builder()
				.holdId(holdId)
				.reservationNumber(reservationNumber)
				.userId(Objects.requireNonNull(userId))
				.sessionToken(sessionToken)
				.showScheduleId(Objects.requireNonNull(showScheduleId))
				.scheduledSeatIds(List.copyOf(Objects.requireNonNull(scheduledSeatIds)))
				.expiresAt(Objects.requireNonNull(expiresAt))
				.build();
		}

		@Override
		public String getEventType() {
			return "HOLD_REQUESTED";
		}

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

	@Getter
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SaleCanceled implements TicketingEvent {
		private String reservationNumber;
		private UUID userId;
		private List<Long> scheduledSeatIds;

		public static SaleCanceled of(Reservation reservation, List<Long> scheduledSeatIds) {
			return SaleCanceled.builder()
				.reservationNumber(reservation.getNumber())
				.userId(reservation.getUserId())
				.scheduledSeatIds(List.copyOf(scheduledSeatIds))
				.build();
		}

		@Override
		public String getEventType() {
			return "SALE_CANCELED";
		}
	}
}
