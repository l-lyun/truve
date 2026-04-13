package com.truve.platform.musical.show.dto;

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

		public static CreatePayment of(Long artistId, String artistName, Long amount, String orderId, String paymentMethod) {
			return new CreatePayment(
				artistId,
				artistName,
				"월간 멤버십",
				amount,
				orderId,
				paymentMethod
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

	public static class Complete {
		private Long artistId;
		private String artistName;
		private String planName;
		private Long amount;
		private String joinedAt;
		private String nextBillingAt;

		public Complete(
			Long artistId,
			String artistName,
			String planName,
			Long amount,
			String joinedAt,
			String nextBillingAt
		) {
			this.artistId = artistId;
			this.artistName = artistName;
			this.planName = planName;
			this.amount = amount;
			this.joinedAt = joinedAt;
			this.nextBillingAt = nextBillingAt;
		}

		public static Complete of(
			Long artistId,
			String artistName,
			Long amount,
			String joinedAt,
			String nextBillingAt
		) {
			return new Complete(
				artistId,
				artistName,
				"월간 멤버십",
				amount,
				joinedAt,
				nextBillingAt
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

		public String getJoinedAt() {
			return joinedAt;
		}

		public String getNextBillingAt() {
			return nextBillingAt;
		}
	}
}