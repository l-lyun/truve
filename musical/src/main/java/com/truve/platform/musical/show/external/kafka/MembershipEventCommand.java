package com.truve.platform.musical.show.external.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class MembershipEventCommand {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Confirmed {
		private String orderId;

		public Confirmed() {
		}

		public String getOrderId() {
			return orderId;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class DepositReceived {
		private String orderId;

		public DepositReceived() {
		}

		public String getOrderId() {
			return orderId;
		}
	}
}