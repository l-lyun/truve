package com.truve.platform.musical.show.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.entity.ArtistMembership;
import com.truve.platform.musical.show.repository.ArtistMembershipRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MembershipStatusScheduler {

	private final ArtistMembershipRepository artistMembershipRepository;

	@Scheduled(fixedDelay = 60000)
	@Transactional
	public void expireScheduledMemberships() {
		LocalDateTime now = LocalDateTime.now();
		List<ArtistMembership> memberships = artistMembershipRepository.findExpiredMemberships(
			ArtistMembershipStatus.CANCEL_SCHEDULED,
			now
		);

		memberships.forEach(membership -> membership.expireCancellation(now));
	}
}