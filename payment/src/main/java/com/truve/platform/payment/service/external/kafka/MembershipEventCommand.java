package com.truve.platform.payment.service.external.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class MembershipEventCommand {

	@JsonIgnoreProperties(value = {"eventType"})
	public interface MembershipEvent {
		String getOrderId();

		String getEventType();
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class Confirmed implements MembershipEvent {
		private String orderId;

		@Override
		public String getEventType() {
			return "CONFIRMED";
		}
	}

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class DepositReceived implements MembershipEvent {
		private String orderId;

		@Override
		public String getEventType() {
			return "DEPOSIT_RECEIVED";
		}
	}
}