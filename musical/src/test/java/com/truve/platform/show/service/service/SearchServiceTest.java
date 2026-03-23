package com.truve.platform.show.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.show.dto.SearchResponse;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.repository.ShowCastingRepository;
import com.truve.platform.musical.show.repository.ShowRepository;
import com.truve.platform.musical.show.service.SearchService;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

	@Mock
	private ShowRepository showRepository;
	@Mock
	private ArtistRepository artistRepository;
	@Mock
	private ShowCastingRepository showCastingRepository;
	@Mock
	private S3Service s3Service;

	@InjectMocks
	private SearchService searchService;

	@Test
	@DisplayName("공백 검색어는 빈 결과를 응답한다.")
	void 공백_검색어_빈결과_응답() {
		SearchResponse.SearchResult result = searchService.search("   ", 0, 20, 0, 20);

		assertEquals("", result.getKeyword());
		assertEquals(0, result.getArtistCount());
		assertEquals(0, result.getShowCount());
		assertTrue(result.getArtists().isEmpty());
		assertTrue(result.getShows().isEmpty());

		verifyNoInteractions(showRepository, artistRepository, showCastingRepository, s3Service);
	}

	@Test
	@DisplayName("배우명 검색 시 출연작 공연도 shows에 포함한다.")
	void 배우명_검색_출연작_공연_포함() {
		ArtistRepository.ArtistSearchProjection artistProjection = org.mockito.Mockito.mock(
			ArtistRepository.ArtistSearchProjection.class
		);
		when(artistProjection.getArtistId()).thenReturn(101L);
		when(artistProjection.getArtistName()).thenReturn("김호영");
		when(artistProjection.getProfileImg()).thenReturn(null);

		ShowCastingRepository.ArtistAppearanceProjection appearance = org.mockito.Mockito.mock(
			ShowCastingRepository.ArtistAppearanceProjection.class
		);
		when(appearance.getArtistId()).thenReturn(101L);
		when(appearance.getShowId()).thenReturn(6L);
		when(appearance.getShowTitle()).thenReturn("테스트 검색 공연");
		when(appearance.getPosterImg()).thenReturn("shows/search-test-poster.jpg");
		when(appearance.getVenueName()).thenReturn("Blue Square");
		when(appearance.getStartTime()).thenReturn(LocalDateTime.of(2026, 3, 20, 19, 0));
		when(appearance.getEndTime()).thenReturn(LocalDateTime.of(2026, 6, 30, 23, 59));

		when(showRepository.searchShows(org.mockito.ArgumentMatchers.eq("김호"), org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
			.thenReturn(List.of());
		when(artistRepository.searchArtists("김호")).thenReturn(List.of(artistProjection));
		when(showCastingRepository.findAppearanceInfoByArtistIds(List.of(101L))).thenReturn(List.of(appearance));
		when(s3Service.getImageUrl("shows/search-test-poster.jpg"))
			.thenReturn("http://localstack:4566/truve-media/shows/search-test-poster.jpg");

		SearchResponse.SearchResult result = searchService.search("김호", 0, 20, 0, 20);

		assertEquals("김호", result.getKeyword());
		assertEquals(1, result.getArtistCount());
		assertEquals(1, result.getShowCount());
		assertEquals(1, result.getArtists().size());
		assertEquals("김호영", result.getArtists().get(0).getArtistName());
		assertEquals("출연: 뮤지컬 <테스트 검색 공연>(2026)", result.getArtists().get(0).getAppearanceInfo());
		assertEquals(1, result.getShows().size());
		assertEquals(6L, result.getShows().get(0).getShowId());
		assertEquals("테스트 검색 공연", result.getShows().get(0).getTitle());
		assertEquals("Blue Square", result.getShows().get(0).getVenueName());
		assertFalse(result.isHasMoreArtists());
		assertFalse(result.isHasMoreShows());
	}
}