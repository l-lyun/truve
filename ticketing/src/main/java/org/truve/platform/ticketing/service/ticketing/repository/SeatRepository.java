package org.truve.platform.ticketing.service.ticketing.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;

import io.lettuce.core.dynamic.annotation.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {
	@Query("SELECT s FROM Seat s " +
		"JOIN FETCH s.seatSection " +
		"WHERE s.id IN :seatIds")
	List<Seat> findAllWithSectionByIds(@Param("seatIds") List<Long> seatIds);
}
