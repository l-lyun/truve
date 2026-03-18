package org.truve.platform.ticketing.service.booking.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ShowInfo {

	@Column(nullable = false)
	private Long showId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String venueName;

	@Column(nullable = false)
	private LocalDateTime startAt;

	@Column(nullable = false)
	private String posterImg;

	@Builder
	public ShowInfo(Long showId, String title, String venueName, LocalDateTime startAt, String posterImg) {
		this.showId = showId;
		this.title = title;
		this.venueName = venueName;
		this.startAt = startAt;
		this.posterImg = posterImg;
	}
}
