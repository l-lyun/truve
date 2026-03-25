package com.truve.platform.musical.show.domain.entity;

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
@Table(name = "artist_notice")
public class ArtistNotice extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private Artist artist;

	@Column(nullable = false)
	private String content;

	@Column(name = "display_order")
	private Integer displayOrder;

	@Builder
	private ArtistNotice(Artist artist, String content, Integer displayOrder) {
		this.artist = artist;
		this.content = content;
		this.displayOrder = displayOrder;
	}
}