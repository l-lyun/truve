package com.truve.platform.show.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.truve.platform.common.response.Paging;
import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.seat.domain.entity.Venue;
import com.truve.platform.musical.seat.domain.repository.VenueRepository;
import com.truve.platform.musical.show.domain.entity.HomeBanner;
import com.truve.platform.musical.show.domain.entity.Show;
import com.truve.platform.musical.show.dto.HomeResponse;
import com.truve.platform.musical.show.repository.HomeBannerRepository;
import com.truve.platform.musical.show.repository.ShowRepository;
import com.truve.platform.musical.show.service.HomeService;

@ExtendWith(MockitoExtension.class)
class HomeServiceTest {

	@Mock
	private HomeBannerRepository homeBannerRepository;
	@Mock
	private ShowRepository showRepository;
	@Mock
	private VenueRepository venueRepository;
	@Mock
	private S3Service s3Service;

	@InjectMocks
	private HomeService homeService;

	@Test
	@DisplayName("홈 공연 목록 조회는 기본 정렬(일간 예매순)로 목록을 응답한다.")
	void 홈_공연_목록_조회_기본정렬_성공() {
		Show show = org.mockito.Mockito.mock(Show.class);
		when(show.getId()).thenReturn(1L);
		when(show.getVenueId()).thenReturn(10L);
		when(show.getTitle()).thenReturn("Wicked");
		when(show.getPosterImg()).thenReturn("shows/1/poster.jpg");
		when(show.getStartTime()).thenReturn(LocalDateTime.of(2025, 11, 29, 10, 0));
		when(show.getEndTime()).thenReturn(LocalDateTime.of(2026, 2, 22, 20, 0));

		Venue venue = org.mockito.Mockito.mock(Venue.class);
		when(venue.getId()).thenReturn(10L);
		when(venue.getName()).thenReturn("샤롯데씨어터");

		when(showRepository.findHomeShowsOrderByDailyRank(
			org.mockito.ArgumentMatchers.isNull(),
			org.mockito.ArgumentMatchers.any(LocalDateTime.class),
			org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 20))
		))
			.thenReturn(new PageImpl<>(List.of(show), PageRequest.of(0, 20), 1));
		when(venueRepository.findAllById(List.of(10L))).thenReturn(List.of(venue));
		when(s3Service.getImageUrl("shows/1/poster.jpg")).thenReturn("https://img.example/shows/1/poster.jpg");

		HomeResponse.ShowList result = homeService.getHomeShows(null, null, new Paging(1, 20));

		assertEquals(1, result.getShows().size());
		assertEquals(1L, result.getShows().get(0).getShowId());
		assertEquals("Wicked", result.getShows().get(0).getShowTitle());
		assertEquals("샤롯데씨어터", result.getShows().get(0).getVenueName());
		assertEquals("2025.11.29 - 2026.02.22", result.getShows().get(0).getDate());
		assertEquals(1, result.getPage().getCurrentPage());
		assertEquals(1, result.getPage().getTotalElements());
		assertEquals(1, result.getPage().getTotalPages());
	}

	@Test
	@DisplayName("홈 배너 조회는 노출 배너를 순서대로 URL 변환해 응답한다.")
	void 홈_배너_조회_성공() {
		Show show1 = org.mockito.Mockito.mock(Show.class);
		when(show1.getId()).thenReturn(1L);
		when(show1.getTitle()).thenReturn("뮤지컬A");
		when(show1.getVenueId()).thenReturn(101L);
		when(show1.getStartTime()).thenReturn(LocalDateTime.of(2026, 3, 1, 0, 0));
		when(show1.getEndTime()).thenReturn(LocalDateTime.of(2026, 5, 31, 23, 59));

		Show show2 = org.mockito.Mockito.mock(Show.class);
		when(show2.getId()).thenReturn(2L);
		when(show2.getTitle()).thenReturn("뮤지컬B");
		when(show2.getVenueId()).thenReturn(102L);
		when(show2.getStartTime()).thenReturn(LocalDateTime.of(2026, 4, 1, 0, 0));
		when(show2.getEndTime()).thenReturn(LocalDateTime.of(2026, 6, 30, 23, 59));

		HomeBanner banner1 = org.mockito.Mockito.mock(HomeBanner.class);
		when(banner1.getId()).thenReturn(11L);
		when(banner1.getShowId()).thenReturn(1L);
		when(banner1.getImageKey()).thenReturn("home/banner1.jpg");
		when(banner1.getDisplayOrder()).thenReturn(1);

		HomeBanner banner2 = org.mockito.Mockito.mock(HomeBanner.class);
		when(banner2.getId()).thenReturn(12L);
		when(banner2.getShowId()).thenReturn(2L);
		when(banner2.getImageKey()).thenReturn("home/banner2.jpg");
		when(banner2.getDisplayOrder()).thenReturn(2);

		Venue venue1 = org.mockito.Mockito.mock(Venue.class);
		when(venue1.getId()).thenReturn(101L);
		when(venue1.getName()).thenReturn("샤롯데씨어터");

		Venue venue2 = org.mockito.Mockito.mock(Venue.class);
		when(venue2.getId()).thenReturn(102L);
		when(venue2.getName()).thenReturn("블루스퀘어");

		when(homeBannerRepository.findActiveBanners(any(PageRequest.class)))
			.thenReturn(List.of(banner1, banner2));
		when(showRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(show1, show2));
		when(venueRepository.findAllById(List.of(101L, 102L))).thenReturn(List.of(venue1, venue2));
		when(s3Service.getImageUrl("home/banner1.jpg")).thenReturn("https://img.example/home/banner1.jpg");
		when(s3Service.getImageUrl("home/banner2.jpg")).thenReturn("https://img.example/home/banner2.jpg");

		HomeResponse.BannerList result = homeService.getHomeBanners();

		assertEquals(2, result.getBanners().size());
		assertEquals(11L, result.getBanners().get(0).getBannerId());
		assertEquals(1L, result.getBanners().get(0).getShowId());
		assertEquals("뮤지컬A", result.getBanners().get(0).getShowTitle());
		assertEquals("샤롯데씨어터", result.getBanners().get(0).getVenueName());
		assertEquals("2026.03.01 - 2026.05.31", result.getBanners().get(0).getDate());
		assertEquals("https://img.example/home/banner1.jpg", result.getBanners().get(0).getPosterUrl());
		assertEquals(1, result.getBanners().get(0).getDisplayOrder());
		verify(homeBannerRepository).findActiveBanners(any(PageRequest.class));
	}

	@Test
	@DisplayName("공연이 삭제된 배너도 isActive면 노출되며 기본값으로 응답한다.")
	void 홈_배너_조회_공연삭제_배너_노출() {
		HomeBanner danglingBanner = org.mockito.Mockito.mock(HomeBanner.class);
		when(danglingBanner.getId()).thenReturn(21L);
		when(danglingBanner.getShowId()).thenReturn(999L);
		when(danglingBanner.getImageKey()).thenReturn("home/banner-dangling.jpg");
		when(danglingBanner.getDisplayOrder()).thenReturn(1);

		when(homeBannerRepository.findActiveBanners(any(PageRequest.class))).thenReturn(List.of(danglingBanner));
		when(showRepository.findAllById(List.of(999L))).thenReturn(List.of());
		when(s3Service.getImageUrl("home/banner-dangling.jpg")).thenReturn("https://img.example/home/banner-dangling.jpg");

		HomeResponse.BannerList result = homeService.getHomeBanners();

		assertEquals(1, result.getBanners().size());
		assertEquals(999L, result.getBanners().get(0).getShowId());
		assertEquals("공연 제목 없음", result.getBanners().get(0).getShowTitle());
		assertEquals("공연장 정보 없음", result.getBanners().get(0).getVenueName());
		assertEquals("기간 미정", result.getBanners().get(0).getDate());
		assertEquals("https://img.example/home/banner-dangling.jpg", result.getBanners().get(0).getPosterUrl());
	}

	@Test
	@DisplayName("공연이 존재하고 종료일이 지난 배너는 노출되지 않는다.")
	void 홈_배너_조회_종료공연_비노출() {
		Show endedShow = org.mockito.Mockito.mock(Show.class);
		when(endedShow.getId()).thenReturn(1L);
		when(endedShow.getEndTime()).thenReturn(LocalDateTime.now().minusDays(1));

		HomeBanner endedBanner = org.mockito.Mockito.mock(HomeBanner.class);
		when(endedBanner.getShowId()).thenReturn(1L);

		when(homeBannerRepository.findActiveBanners(any(PageRequest.class))).thenReturn(List.of(endedBanner));
		when(showRepository.findAllById(List.of(1L))).thenReturn(List.of(endedShow));

		HomeResponse.BannerList result = homeService.getHomeBanners();

		assertEquals(0, result.getBanners().size());
	}
}
