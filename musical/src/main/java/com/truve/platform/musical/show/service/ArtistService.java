package com.truve.platform.musical.show.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

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
		boolean artistExists = artistRepository.existsById(artistId);
		if (!artistExists) {
			return;
		}

		boolean exists = artistLikeRepository.existsByUserIdAndArtistId(userId, artistId);
		if (exists) {
			return;
		}

		Artist artist = artistRepository.getReferenceById(artistId);
		ArtistLike artistLike = ArtistLike.builder()
			.userId(userId)
			.artist(artist)
			.build();
		try {
			artistLikeRepository.save(artistLike);
		} catch (DataIntegrityViolationException e) {
			boolean alreadyExists = artistLikeRepository.existsByUserIdAndArtistId(userId, artistId);
			if (alreadyExists) {
				return;
			}
			throw e;
		}
	}

	@Transactional
	public void unlikeArtist(Long artistId, Long userId) {
		artistLikeRepository.deleteByUserIdAndArtistId(userId, artistId);
	}
}
