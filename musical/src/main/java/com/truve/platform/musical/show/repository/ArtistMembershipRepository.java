package com.truve.platform.musical.show.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.show.domain.entity.ArtistMembership;

public interface ArtistMembershipRepository extends JpaRepository<ArtistMembership, Long> {

	boolean existsByUserIdAndArtistId(UUID userId, Long artistId);
}