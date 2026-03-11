package com.truve.platform.show.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.repository.ArtistLikeRepository;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.service.ArtistService;

@ExtendWith(MockitoExtension.class)
class ArtistServiceTest {

	@Mock
	private ArtistRepository artistRepository;
	@Mock
	private ArtistLikeRepository artistLikeRepository;

	@InjectMocks
	private ArtistService artistService;

	@Test
	@DisplayName("배우 좋아요 등록은 처음 요청일 때만 저장된다.")
	void 배우_좋아요_등록_성공() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		when(artistRepository.existsById(101L)).thenReturn(true);
		when(artistRepository.getReferenceById(101L)).thenReturn(artist);
		when(artistLikeRepository.existsByUserIdAndArtistId(userId, 101L)).thenReturn(false);

		artistService.likeArtist(101L, userId);

		verify(artistLikeRepository).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("이미 좋아요한 배우는 예외를 발생시킨다.")
	void 배우_좋아요_등록_중복_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		when(artistRepository.existsById(101L)).thenReturn(true);
		when(artistLikeRepository.existsByUserIdAndArtistId(userId, 101L)).thenReturn(true);

		CustomException exception = assertThrows(CustomException.class, () -> artistService.likeArtist(101L, userId));

		assertEquals(ErrorCode.ALREADY_LIKED_ARTIST, exception.getErrorCode());
		verify(artistLikeRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("배우 좋아요 취소는 사용자-배우 기준으로 삭제한다.")
	void 배우_좋아요_취소_성공() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		artistService.unlikeArtist(101L, userId);

		verify(artistLikeRepository).deleteByUserIdAndArtistId(userId, 101L);
	}

	@Test
	@DisplayName("존재하지 않는 배우 좋아요 등록 요청은 예외를 발생시킨다.")
	void 존재하지않는_배우_좋아요_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		when(artistRepository.existsById(999L)).thenReturn(false);

		CustomException exception = assertThrows(CustomException.class, () -> artistService.likeArtist(999L, userId));

		assertEquals(ErrorCode.NOT_FOUND_ARTIST, exception.getErrorCode());
		verify(artistLikeRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("동시성으로 중복 저장 충돌이 발생하면 이미 좋아요 에러를 응답한다.")
	void 배우_좋아요_등록_동시성_중복충돌_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		Artist artist = org.mockito.Mockito.mock(Artist.class);
		when(artistRepository.existsById(101L)).thenReturn(true);
		when(artistRepository.getReferenceById(101L)).thenReturn(artist);
		when(artistLikeRepository.existsByUserIdAndArtistId(userId, 101L)).thenReturn(false, true);
		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(artistLikeRepository).save(org.mockito.ArgumentMatchers.any());

		CustomException exception = assertThrows(CustomException.class, () -> artistService.likeArtist(101L, userId));

		assertEquals(ErrorCode.ALREADY_LIKED_ARTIST, exception.getErrorCode());
		verify(artistLikeRepository).save(org.mockito.ArgumentMatchers.any());
	}
}
