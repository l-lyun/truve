package com.truve.platform.show.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.seat.domain.entity.Venue;
import com.truve.platform.musical.seat.domain.repository.VenueRepository;
import com.truve.platform.musical.show.domain.constant.ShowScheduleStatus;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.domain.entity.Show;
import com.truve.platform.musical.show.domain.entity.ShowCasting;
import com.truve.platform.musical.show.domain.entity.ShowSchedule;
import com.truve.platform.musical.show.domain.entity.ShowSectionGrade;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.repository.ArtistLikeRepository;
import com.truve.platform.musical.show.repository.ShowCastingRepository;
import com.truve.platform.musical.show.repository.ShowRepository;
import com.truve.platform.musical.show.repository.ShowScheduleRepository;
import com.truve.platform.musical.show.repository.ShowSeatGradeRepository;
import com.truve.platform.musical.show.service.ShowService;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {

	@Mock
	private ShowRepository showRepository;
	@Mock
	private ShowCastingRepository showCastingRepository;
	@Mock
	private VenueRepository venueRepository;
	@Mock
	private ShowScheduleRepository showScheduleRepository;
	@Mock
	private ArtistLikeRepository artistLikeRepository;
	@Mock
	private ShowSeatGradeRepository showSeatGradeRepository;
	@Mock
	private S3Service s3Service;

	@InjectMocks
	private ShowService showService;

	@Test
	@DisplayName("공연 상세는 조회된 공연 전체 캐스팅을 응답한다.")
	void 공연_상세_공연전체_캐스팅_응답_성공() {
		Long showId = 1L;

		Show show = org.mockito.Mockito.mock(Show.class);
		Venue venue = org.mockito.Mockito.mock(Venue.class);
		when(show.getId()).thenReturn(showId);
		when(show.getTitle()).thenReturn("뮤지컬");
		when(show.getDescription()).thenReturn("설명");
		when(show.getRuntimeMin()).thenReturn(120);
		when(show.getAgeLimit()).thenReturn(8);
		when(show.getPosterImg()).thenReturn("poster.jpg");
		when(show.getNoticeImg()).thenReturn("notice.jpg");
		when(s3Service.getImageUrl("poster.jpg")).thenReturn("https://img/poster.jpg");
		when(s3Service.getImageUrl("notice.jpg")).thenReturn(null);
		when(show.getStartTime()).thenReturn(LocalDateTime.of(2026, 3, 1, 0, 0));
		when(show.getEndTime()).thenReturn(LocalDateTime.of(2026, 4, 1, 0, 0));
		when(show.getVenueId()).thenReturn(10L);
		when(venue.getName()).thenReturn("예술의전당");
		when(venue.getAddress()).thenReturn("서울");

		ShowSchedule schedule1 = org.mockito.Mockito.mock(ShowSchedule.class);
		ShowSchedule schedule2 = org.mockito.Mockito.mock(ShowSchedule.class);
		when(schedule1.getId()).thenReturn(2001L);
		when(schedule1.getShowTime()).thenReturn(LocalDateTime.of(2026, 3, 2, 19, 30));
		when(schedule1.getStatus()).thenReturn(ShowScheduleStatus.OPEN);
		when(schedule2.getId()).thenReturn(2002L);
		when(schedule2.getShowTime()).thenReturn(LocalDateTime.of(2026, 3, 3, 19, 30));
		when(schedule2.getStatus()).thenReturn(ShowScheduleStatus.CANCELLED);

		Artist artistA = org.mockito.Mockito.mock(Artist.class);
		Artist artistB = org.mockito.Mockito.mock(Artist.class);
		Artist artistC = org.mockito.Mockito.mock(Artist.class);
		when(artistA.getId()).thenReturn(101L);
		when(artistA.getName()).thenReturn("배우A");
		when(artistA.getProfileImg()).thenReturn("artistA.jpg");
		when(s3Service.getImageUrl("artistA.jpg")).thenReturn("https://img.example/artistA.jpg");
		when(artistB.getId()).thenReturn(102L);
		when(artistB.getName()).thenReturn("배우B");
		when(artistB.getProfileImg()).thenReturn("artistB.jpg");
		when(s3Service.getImageUrl("artistB.jpg")).thenReturn("https://img.example/artistB.jpg");
		when(artistC.getId()).thenReturn(103L);
		when(artistC.getName()).thenReturn("배우C");
		when(artistC.getProfileImg()).thenReturn("artistC.jpg");
		when(s3Service.getImageUrl("artistC.jpg")).thenReturn("https://img.example/artistC.jpg");

		ShowCasting castOrder1 = org.mockito.Mockito.mock(ShowCasting.class);
		ShowCasting castOrder2 = org.mockito.Mockito.mock(ShowCasting.class);
		ShowCasting castOrderNull = org.mockito.Mockito.mock(ShowCasting.class);
		when(castOrder1.getId()).thenReturn(5001L);
		when(castOrder1.getArtist()).thenReturn(artistA);
		when(castOrder1.getRoleName()).thenReturn("주연");
		when(castOrder1.getCastingOrder()).thenReturn(1);
		when(castOrder2.getId()).thenReturn(5002L);
		when(castOrder2.getArtist()).thenReturn(artistB);
		when(castOrder2.getRoleName()).thenReturn("조연");
		when(castOrder2.getCastingOrder()).thenReturn(2);
		when(castOrderNull.getId()).thenReturn(5003L);
		when(castOrderNull.getArtist()).thenReturn(artistC);
		when(castOrderNull.getRoleName()).thenReturn("특별출연");
		when(castOrderNull.getCastingOrder()).thenReturn(null);

		ShowSectionGrade seat = org.mockito.Mockito.mock(ShowSectionGrade.class);
		when(seat.getId()).thenReturn(7001L);
		when(seat.getGradeName()).thenReturn("VIP");
		when(seat.getColorCode()).thenReturn("#FFD700");

		when(showRepository.findByIdOrThrow(showId)).thenReturn(show);
		when(venueRepository.findById(10L)).thenReturn(Optional.of(venue));
		when(showScheduleRepository.findSchedules(showId)).thenReturn(List.of(schedule1, schedule2));
		when(showCastingRepository.findAllByShowId(showId))
			.thenReturn(List.of(castOrder1, castOrder2, castOrderNull));
		when(artistLikeRepository.findLikedArtistIds(7L, List.of(101L, 102L, 103L)))
			.thenReturn(List.of(101L));
		when(showSeatGradeRepository.findSeatPrices(showId)).thenReturn(List.of(seat));

		ShowResponse.Detail result = showService.getDetail(showId, 7L);

		assertEquals(2, result.getSchedules().size());
		assertEquals("OPEN", result.getSchedules().get(0).getStatus());
		assertEquals("CANCELLED", result.getSchedules().get(1).getStatus());

		List<ShowResponse.Casting> castings = result.getCastings();
		assertEquals(3, castings.size());
		assertEquals("배우A", castings.get(0).getArtistName());
		assertEquals("https://img.example/artistA.jpg", castings.get(0).getProfileImageUrl());
		assertEquals(1, castings.get(0).getOrder());
		assertEquals("배우B", castings.get(1).getArtistName());
		assertEquals(2, castings.get(1).getOrder());
		assertEquals("배우C", castings.get(2).getArtistName());
		assertNull(castings.get(2).getOrder());

		assertTrue(castings.get(0).getIsLiked());
		assertFalse(castings.get(1).getIsLiked());
		assertFalse(castings.get(2).getIsLiked());
	}
}
