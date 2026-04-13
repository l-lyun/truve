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

	public static class MyMembership {
		private MyMembershipSummary summary;
		private java.util.List<MyMembershipItem> memberships;

		public MyMembership(MyMembershipSummary summary, java.util.List<MyMembershipItem> memberships) {
			this.summary = summary;
			this.memberships = memberships;
		}

		public static MyMembership of(MyMembershipSummary summary, java.util.List<MyMembershipItem> memberships) {
			return new MyMembership(summary, memberships);
		}

		public MyMembershipSummary getSummary() {
			return summary;
		}

		public java.util.List<MyMembershipItem> getMemberships() {
			return memberships;
		}
	}

	public static class MyMembershipSummary {
		private long activeMembershipCount;
		private long monthlyPaymentAmount;

		public MyMembershipSummary(long activeMembershipCount, long monthlyPaymentAmount) {
			this.activeMembershipCount = activeMembershipCount;
			this.monthlyPaymentAmount = monthlyPaymentAmount;
		}

		public static MyMembershipSummary of(long activeMembershipCount, long monthlyPaymentAmount) {
			return new MyMembershipSummary(activeMembershipCount, monthlyPaymentAmount);
		}

		public long getActiveMembershipCount() {
			return activeMembershipCount;
		}

		public long getMonthlyPaymentAmount() {
			return monthlyPaymentAmount;
		}
	}

	public static class MyMembershipItem {
		private Long membershipId;
		private Long artistId;
		private String artistName;
		private String profileImageUrl;
		private String status;
		private String statusLabel;
		private String joinedAt;
		private String nextBillingAt;
		private long remainingDays;
		private long monthlyAmount;
		private boolean cancelable;

		public MyMembershipItem(
			Long membershipId,
			Long artistId,
			String artistName,
			String profileImageUrl,
			String status,
			String statusLabel,
			String joinedAt,
			String nextBillingAt,
			long remainingDays,
			long monthlyAmount,
			boolean cancelable
		) {
			this.membershipId = membershipId;
			this.artistId = artistId;
			this.artistName = artistName;
			this.profileImageUrl = profileImageUrl;
			this.status = status;
			this.statusLabel = statusLabel;
			this.joinedAt = joinedAt;
			this.nextBillingAt = nextBillingAt;
			this.remainingDays = remainingDays;
			this.monthlyAmount = monthlyAmount;
			this.cancelable = cancelable;
		}

		public static MyMembershipItem of(
			Long membershipId,
			Long artistId,
			String artistName,
			String profileImageUrl,
			String status,
			String statusLabel,
			String joinedAt,
			String nextBillingAt,
			long remainingDays,
			long monthlyAmount,
			boolean cancelable
		) {
			return new MyMembershipItem(
				membershipId,
				artistId,
				artistName,
				profileImageUrl,
				status,
				statusLabel,
				joinedAt,
				nextBillingAt,
				remainingDays,
				monthlyAmount,
				cancelable
			);
		}

		public Long getMembershipId() {
			return membershipId;
		}

		public Long getArtistId() {
			return artistId;
		}

		public String getArtistName() {
			return artistName;
		}

		public String getProfileImageUrl() {
			return profileImageUrl;
		}

		public String getStatus() {
			return status;
		}

		public String getStatusLabel() {
			return statusLabel;
		}

		public String getJoinedAt() {
			return joinedAt;
		}

		public String getNextBillingAt() {
			return nextBillingAt;
		}

		public long getRemainingDays() {
			return remainingDays;
		}

		public long getMonthlyAmount() {
			return monthlyAmount;
		}

		public boolean isCancelable() {
			return cancelable;
		}
	}
}