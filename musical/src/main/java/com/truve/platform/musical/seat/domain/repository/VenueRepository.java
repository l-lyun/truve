package com.truve.platform.musical.seat.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.seat.domain.entity.Venue;

public interface VenueRepository extends JpaRepository<Venue, Long> {
}
