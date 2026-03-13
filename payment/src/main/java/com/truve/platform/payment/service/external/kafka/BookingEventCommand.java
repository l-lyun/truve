package com.truve.platform.payment.service.external.kafka;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class BookingEventCommand {

	@JsonIgnoreProperties(value = {"eventType"})
	public interface BookingEvent {
		String getReservationNumber();

		String getEventType();
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Confirmed implements BookingEvent {
		private String reservationNumber;
		@JsonFormat(shape = JsonFormat.Shape.ARRAY)
		private LocalDateTime paidAt;
		@JsonProperty("depositPending")
		private boolean isDepositPending;

		@Override
		public String getEventType() {
			return "CONFIRMED";
		}
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class DepositReceived implements BookingEvent {
		private String reservationNumber;
		@JsonFormat(shape = JsonFormat.Shape.ARRAY)
		private LocalDateTime paidAt;

		@Override
		public String getEventType() {
			return "DEPOSIT_RECEIVED";
		}
	}
}
