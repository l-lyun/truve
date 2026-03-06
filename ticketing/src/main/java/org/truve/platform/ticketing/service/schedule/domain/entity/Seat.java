package org.truve.platform.ticketing.service.schedule.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seat")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Seat extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "seat_section_id")
	private SeatSection seatSection;

	@Column(nullable = false)
	private String seatRow;

	@Column(nullable = false)
	private Long seatNumber;


	@Builder
	public Seat(SeatSection seatSection, String seatRow, Long seatNumber) {
		this.seatSection = seatSection;
		this.seatRow = seatRow;
		this.seatNumber = seatNumber;
	}
}
