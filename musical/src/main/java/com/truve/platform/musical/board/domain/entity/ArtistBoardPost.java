package com.truve.platform.musical.board.domain.entity;

import java.util.List;

import com.truve.platform.common.support.BaseEntity;
import com.truve.platform.musical.show.domain.converter.StringListConverter;
import com.truve.platform.musical.show.domain.entity.Artist;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "artist_board_posts")
public class ArtistBoardPost extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "artist_id", nullable = false)
	private Artist artist;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Convert(converter = StringListConverter.class)
	@Column(name = "image_keys", length = 2000)
	private List<String> imageKeys;

	@Builder
	private ArtistBoardPost(Artist artist, String content, List<String> imageKeys) {
		this.artist = artist;
		this.content = content;
		this.imageKeys = imageKeys;
	}
}
