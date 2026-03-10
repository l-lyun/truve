package com.truve.platform.musical.show.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.domain.entity.ArtistLike;
import com.truve.platform.musical.show.repository.ArtistLikeRepository;
import com.truve.platform.musical.show.repository.ArtistRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtistService {

	private final ArtistRepository artistRepository;
	private final ArtistLikeRepository artistLikeRepository;

	@Transactional
	public void likeArtist(Long artistId, Long userId) {
		Preconditions.validate(artistRepository.existsById(artistId), ErrorCode.NOT_FOUND_ARTIST);
		Preconditions.validate(
			!artistLikeRepository.existsByUserIdAndArtistId(userId, artistId),
			ErrorCode.ALREADY_LIKED_ARTIST
		);

		Artist artist = artistRepository.getReferenceById(artistId);
		ArtistLike artistLike = ArtistLike.builder()
			.userId(userId)
			.artist(artist)
			.build();
		try {
			artistLikeRepository.save(artistLike);
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(ErrorCode.ALREADY_LIKED_ARTIST);
		}
	}

	@Transactional
	public void unlikeArtist(Long artistId, Long userId) {
		artistLikeRepository.deleteByUserIdAndArtistId(userId, artistId);
	}
}
