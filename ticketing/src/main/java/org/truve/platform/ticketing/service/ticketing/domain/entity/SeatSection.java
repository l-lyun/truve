package org.truve.platform.ticketing.service.ticketing.domain.entity;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seat_section")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class SeatSection extends BaseEntity {

	@Column(nullable = false)
	private Long venueId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private Long floor;

	@Column(nullable = false)
	private String gradeName;

	@Column(nullable = false)
	private Long price;

	@Builder
	public SeatSection(Long venueId, String name, Long floor,  String gradeName, Long price) {
		this.venueId = venueId;
		this.name = name;
		this.floor = floor;
		this.gradeName = gradeName;
		this.price = price;
	}
}
