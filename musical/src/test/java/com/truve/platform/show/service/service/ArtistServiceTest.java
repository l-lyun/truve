package com.truve.platform.show.service.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.response.Paging;
import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.dto.ArtistResponse;
import com.truve.platform.musical.show.repository.ArtistLikeRepository;
import com.truve.platform.musical.show.repository.ArtistMembershipRepository;
import com.truve.platform.musical.show.repository.ArtistNoticeRepository;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.repository.ShowCastingRepository;
import com.truve.platform.musical.show.service.ArtistService;

@ExtendWith(MockitoExtension.class)
class ArtistServiceTest {

	@Mock
	private ArtistRepository artistRepository;
	@Mock
	private ArtistLikeRepository artistLikeRepository;
	@Mock
	private ArtistMembershipRepository artistMembershipRepository;
	@Mock
	private ArtistNoticeRepository artistNoticeRepository;
	@Mock
	private ShowCastingRepository showCastingRepository;
	@Mock
	private S3Service s3Service;

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

		verify(artistLikeRepository).save(any());
	}

	@Test
	@DisplayName("이미 좋아요한 배우는 예외를 발생시킨다.")
	void 배우_좋아요_등록_중복_실패() {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		when(artistRepository.existsById(101L)).thenReturn(true);
		when(artistLikeRepository.existsByUserIdAndArtistId(userId, 101L)).thenReturn(true);

		CustomException exception = assertThrows(CustomException.class, () -> artistService.likeArtist(101L, userId));

		assertEquals(ErrorCode.ALREADY_LIKED_ARTIST, exception.getErrorCode());
		verify(artistLikeRepository, never()).save(any());
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
		verify(artistLikeRepository, never()).save(any());
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
			.when(artistLikeRepository).save(any());

		CustomException exception = assertThrows(CustomException.class, () -> artistService.likeArtist(101L, userId));

		assertEquals(ErrorCode.ALREADY_LIKED_ARTIST, exception.getErrorCode());
		verify(artistLikeRepository).save(any());
	}

	@Test
	@DisplayName("비로그인 아티스트 상세 조회는 liked, joined를 false로 응답한다.")
	void 아티스트_상세_조회_비로그인_성공() {
		ArtistRepository.ArtistDetailProjection artist = org.mockito.Mockito.mock(ArtistRepository.ArtistDetailProjection.class);
		ShowCastingRepository.ArtistShowSummaryProjection currentShow = org.mockito.Mockito.mock(ShowCastingRepository.ArtistShowSummaryProjection.class);
		when(artist.getArtistId()).thenReturn(1L);
		when(artist.getArtistName()).thenReturn("이재환");
		when(artist.getProfileImg()).thenReturn("artists/lee.png");
		when(artistRepository.findDetailById(1L)).thenReturn(Optional.of(artist));
		when(artistNoticeRepository.findNoticesByArtistId(1L)).thenReturn(List.of());
		when(showCastingRepository.findCurrentShowsByArtistId(eq(1L), any(LocalDateTime.class))).thenReturn(
			List.of(currentShow));
		when(showCastingRepository.findPastShowsByArtistId(any(Long.class), any(LocalDateTime.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 13), 0));
		when(currentShow.getShowId()).thenReturn(10L);
		when(currentShow.getShowTitle()).thenReturn("킹키부츠");
		when(currentShow.getPosterImg()).thenReturn("shows/current.png");
		when(currentShow.getVenueName()).thenReturn("샤롯데씨어터");
		when(currentShow.getStartTime()).thenReturn(LocalDateTime.of(2026, 3, 11, 0, 0));
		when(currentShow.getEndTime()).thenReturn(LocalDateTime.of(2026, 6, 21, 0, 0));
		when(s3Service.getImageUrl("artists/lee.png")).thenReturn("https://img.example/lee.png");
		when(s3Service.getImageUrl("shows/current.png")).thenReturn("https://img.example/current.png");

		ArtistResponse.Detail response = artistService.getDetail(1L, null);

		assertThat(response.getArtist().getIsLiked()).isFalse();
		assertThat(response.getMembership().getJoined()).isFalse();
		assertThat(response.getArtist().getProfileImageUrl()).isEqualTo("https://img.example/lee.png");
		assertThat(response.getCurrentShows()).hasSize(1);
		assertThat(response.getCurrentShows().get(0).getPosterUrl()).isEqualTo("https://img.example/current.png");
	}

	@Test
	@DisplayName("아티스트 지난 출연 작품 조회는 페이지 데이터를 응답한다.")
	void 아티스트_지난출연작품_조회_성공() {
		Paging paging = new Paging();
		ShowCastingRepository.ArtistShowSummaryProjection show = org.mockito.Mockito.mock(ShowCastingRepository.ArtistShowSummaryProjection.class);
		when(artistRepository.existsById(1L)).thenReturn(true);
		when(showCastingRepository.findPastShowsByArtistId(any(Long.class), any(LocalDateTime.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(show), PageRequest.of(0, 10), 1));
		when(show.getShowId()).thenReturn(21L);
		when(show.getShowTitle()).thenReturn("지난 공연");
		when(show.getPosterImg()).thenReturn("shows/past.png");
		when(show.getVenueName()).thenReturn("샤롯데씨어터");
		when(show.getStartTime()).thenReturn(LocalDateTime.of(2024, 1, 5, 0, 0));
		when(show.getEndTime()).thenReturn(LocalDateTime.of(2024, 2, 18, 0, 0));
		when(s3Service.getImageUrl("shows/past.png")).thenReturn("https://img.example/past.png");

		var response = artistService.getPastShows(1L, paging);

		assertThat(response.getContent()).hasSize(1);
		assertThat(response.getContent().get(0).getShowId()).isEqualTo(21L);
		assertThat(response.getContent().get(0).getPosterUrl()).isEqualTo("https://img.example/past.png");
		assertThat(response.getTotalElements()).isEqualTo(1L);
	}
}