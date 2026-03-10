package com.truve.platform.musical.show.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.truve.platform.musical.show.domain.entity.Artist;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
}
