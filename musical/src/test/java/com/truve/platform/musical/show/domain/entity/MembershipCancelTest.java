package com.truve.platform.musical.show.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.constant.MembershipPaymentMethod;

class MembershipCancelTest {

	@Test
	@DisplayName("해지 예정 멤버십은 다음 결제일이 지나면 CANCELED로 전이된다.")
	void 해지예정_멤버십_만료() {
		ArtistMembership membership = ArtistMembership.builder()
			.userId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
			.artist(null)
			.status(ArtistMembershipStatus.CANCEL_SCHEDULED)
			.orderId("M20260408123456")
			.monthlyAmount(5_000L)
			.paymentMethod(MembershipPaymentMethod.TOSS_PAY)
			.joinedAt(LocalDateTime.of(2026, 4, 1, 10, 0))
			.nextBillingAt(LocalDateTime.of(2026, 4, 8, 10, 0))
			.build();

		membership.expireCancellation(LocalDateTime.of(2026, 4, 8, 10, 0));

		assertThat(membership.getStatus()).isEqualTo(ArtistMembershipStatus.CANCELED);
	}
}