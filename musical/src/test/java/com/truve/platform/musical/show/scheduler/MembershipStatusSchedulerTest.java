package com.truve.platform.musical.show.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.constant.MembershipPaymentMethod;
import com.truve.platform.musical.show.domain.entity.ArtistMembership;
import com.truve.platform.musical.show.repository.ArtistMembershipRepository;

@ExtendWith(MockitoExtension.class)
class MembershipStatusSchedulerTest {

	@Mock
	private ArtistMembershipRepository artistMembershipRepository;

	@InjectMocks
	private MembershipStatusScheduler membershipStatusScheduler;

	@Test
	@DisplayName("스케줄러는 만료된 해지 예정 멤버십을 조회한다.")
	void 만료된_해지예정_멤버십_조회() {
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

		when(artistMembershipRepository.findExpiredMemberships(
				org.mockito.ArgumentMatchers.eq(ArtistMembershipStatus.CANCEL_SCHEDULED),
				org.mockito.ArgumentMatchers.any(LocalDateTime.class)
			))
			.thenReturn(List.of(membership));

		membershipStatusScheduler.expireScheduledMemberships();

		verify(artistMembershipRepository).findExpiredMemberships(
			org.mockito.ArgumentMatchers.eq(ArtistMembershipStatus.CANCEL_SCHEDULED),
			org.mockito.ArgumentMatchers.any(LocalDateTime.class)
		);
		assertThat(membership.getStatus()).isEqualTo(ArtistMembershipStatus.CANCELED);
	}
}