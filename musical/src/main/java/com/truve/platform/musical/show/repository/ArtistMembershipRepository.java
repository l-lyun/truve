package com.truve.platform.musical.show.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.entity.ArtistMembership;

public interface ArtistMembershipRepository extends JpaRepository<ArtistMembership, Long> {

	Optional<ArtistMembership> findByUserIdAndArtistId(UUID userId, Long artistId);

	Optional<ArtistMembership> findByOrderId(String orderId);

	boolean existsByUserIdAndArtistIdAndStatus(UUID userId, Long artistId, ArtistMembershipStatus status);

	@EntityGraph(attributePaths = {"artist"})
	@Query("""
		select am
		from ArtistMembership am
		where am.userId = :userId
		and am.status in :statuses
		and am.nextBillingAt > :now
		order by am.nextBillingAt asc, am.joinedAt desc, am.id asc
	""")
	List<ArtistMembership> findCurrentMemberships(
		@Param("userId") UUID userId,
		@Param("statuses") Collection<ArtistMembershipStatus> statuses,
		@Param("now") LocalDateTime now
	);

	@Query("""
		select am
		from ArtistMembership am
		where am.status = :status
		and am.nextBillingAt <= :nextBillingAt
	""")
	List<ArtistMembership> findExpiredMemberships(
		@Param("status") ArtistMembershipStatus status,
		@Param("nextBillingAt") LocalDateTime nextBillingAt
	);
}