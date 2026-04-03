package com.truve.platform.musical.show.dto;

import com.truve.platform.musical.show.domain.entity.ArtistMembership;

public class MembershipResponse {

	public static class CreatePayment {
		private Long artistId;
		private String artistName;
		private String planName;
		private Long amount;
		private String orderId;
		private String paymentMethod;

		public CreatePayment(
			Long artistId,
			String artistName,
			String planName,
			Long amount,
			String orderId,
			String paymentMethod
		) {
			this.artistId = artistId;
			this.artistName = artistName;
			this.planName = planName;
			this.amount = amount;
			this.orderId = orderId;
			this.paymentMethod = paymentMethod;
		}

		public static CreatePayment of(Long artistId, String artistName, ArtistMembership membership) {
			return new CreatePayment(
				artistId,
				artistName,
				"월간 멤버십",
				membership.getMonthlyAmount(),
				membership.getOrderId(),
				membership.getPaymentMethod().getDisplayName()
			);
		}

		public Long getArtistId() {
			return artistId;
		}

		public String getArtistName() {
			return artistName;
		}

		public String getPlanName() {
			return planName;
		}

		public Long getAmount() {
			return amount;
		}

		public String getOrderId() {
			return orderId;
		}

		public String getPaymentMethod() {
			return paymentMethod;
		}
	}
}