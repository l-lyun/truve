package org.truve.platform.ticketing.service.domain.entity;

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
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reservation_seat_mapping")
public class ReservationSeat extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reservation_id", nullable = false)
	private Reservation reservation;

	@Column(nullable = false)
	private Long scheduleSeatMappingId;

	@Column(nullable = false)
	private Long priceSnapshot;

	@Builder
	public ReservationSeat(Reservation reservation, Long scheduleSeatMappingId, Long priceSnapshot) {
		this.reservation = reservation;
		this.scheduleSeatMappingId = scheduleSeatMappingId;
		this.priceSnapshot = priceSnapshot;
	}

}
