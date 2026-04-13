package com.truve.platform.musical.show.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.constant.MembershipPaymentMethod;

class ArtistMembershipTest {

	@Test
	@DisplayName("PAYMENT_PENDING 멤버십은 결제 완료 시 ACTIVE로 전환된다.")
	void 결제완료시_ACTIVE_전환() {
		ArtistMembership membership = ArtistMembership.preparePayment(
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			null,
			"M20260402123456",
			5_000L,
			MembershipPaymentMethod.TOSS_PAY
		);

		membership.confirm();

		assertThat(membership.getStatus()).isEqualTo(ArtistMembershipStatus.ACTIVE);
	}
}
