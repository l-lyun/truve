package com.truve.platform.musical.show.domain.entity;

import java.util.UUID;

import com.truve.platform.common.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
name = "artist_memberships",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_artist_memberships_user_artist",
			columnNames = {"user_id", "artist_id"}
		)
	}
)
public class ArtistMembership extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private Artist artist;

	@Builder
	private ArtistMembership(UUID userId, Artist artist) {
		this.userId = userId;
		this.artist = artist;
	}
}