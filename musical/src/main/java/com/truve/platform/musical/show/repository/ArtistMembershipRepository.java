package com.truve.platform.musical.show.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.show.domain.constant.ArtistMembershipStatus;
import com.truve.platform.musical.show.domain.entity.ArtistMembership;

public interface ArtistMembershipRepository extends JpaRepository<ArtistMembership, Long> {

	Optional<ArtistMembership> findByUserIdAndArtistId(UUID userId, Long artistId);

	Optional<ArtistMembership> findByOrderId(String orderId);

	boolean existsByUserIdAndArtistIdAndStatus(UUID userId, Long artistId, ArtistMembershipStatus status);
}