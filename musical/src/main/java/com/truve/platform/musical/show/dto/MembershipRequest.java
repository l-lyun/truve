package com.truve.platform.musical.show.dto;

import com.truve.platform.musical.show.domain.constant.MembershipPaymentMethod;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public class MembershipRequest {

	public static class CreatePayment {
		@NotNull(message = "결제 수단을 선택해 주세요.")
		private MembershipPaymentMethod paymentMethod;

		@AssertTrue(message = "이용약관에 동의해 주세요.")
		private boolean termsAgreed;

		@AssertTrue(message = "개인정보 수집 및 이용에 동의해 주세요.")
		private boolean privacyAgreed;

		@AssertTrue(message = "자동결제에 동의해 주세요.")
		private boolean autoPaymentAgreed;

		public CreatePayment() {
		}

		public CreatePayment(
			MembershipPaymentMethod paymentMethod,
			boolean termsAgreed,
			boolean privacyAgreed,
			boolean autoPaymentAgreed
		) {
			this.paymentMethod = paymentMethod;
			this.termsAgreed = termsAgreed;
			this.privacyAgreed = privacyAgreed;
			this.autoPaymentAgreed = autoPaymentAgreed;
		}

		public MembershipPaymentMethod getPaymentMethod() {
			return paymentMethod;
		}

		public boolean isTermsAgreed() {
			return termsAgreed;
		}

		public boolean isPrivacyAgreed() {
			return privacyAgreed;
		}

		public boolean isAutoPaymentAgreed() {
			return autoPaymentAgreed;
		}
	}
}