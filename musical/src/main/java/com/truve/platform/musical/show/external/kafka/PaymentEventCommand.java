package com.truve.platform.musical.show.external.kafka;

import com.truve.platform.musical.show.domain.entity.ArtistMembership;

public class PaymentEventCommand {

	public static class Create {
		private String orderId;
		private Long amount;

		public Create(String orderId, Long amount) {
			this.orderId = orderId;
			this.amount = amount;
		}

		public static Create of(ArtistMembership membership) {
			return new Create(membership.getOrderId(), membership.getMonthlyAmount());
		}

		public String getOrderId() {
			return orderId;
		}

		public Long getAmount() {
			return amount;
		}
	}
}