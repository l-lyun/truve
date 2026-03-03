package com.truve.platform.show.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.show.service.domain.constant.ShowScheduleStatus;
import com.truve.platform.show.service.domain.entity.Artist;
import com.truve.platform.show.service.domain.entity.Show;
import com.truve.platform.show.service.domain.entity.ShowCasting;
import com.truve.platform.show.service.domain.entity.ShowSchedule;
import com.truve.platform.show.service.domain.entity.ShowScheduleCasting;
import com.truve.platform.show.service.domain.entity.ShowSeatGrade;
import com.truve.platform.show.service.domain.entity.Venue;
import com.truve.platform.show.service.dto.ShowResponse;
import com.truve.platform.show.service.repository.ShowRepository;
import com.truve.platform.show.service.repository.ShowScheduleCastingRepository;
import com.truve.platform.show.service.repository.ShowScheduleRepository;
import com.truve.platform.show.service.repository.ShowSeatGradeRepository;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {

	@Mock
	private ShowRepository showRepository;
	@Mock
	private ShowScheduleRepository showScheduleRepository;
	@Mock
	private ShowScheduleCastingRepository showScheduleCastingRepository;
	@Mock
	private ShowSeatGradeRepository showSeatGradeRepository;

	@InjectMocks
	private ShowService showService;

	@Test
	@DisplayName("회차별 캐스팅은 회차 기준으로 그룹핑되고 order 오름차순으로 정렬된다.")
	void 공연_상세_회차별_캐스팅_그룹핑_정렬_성공() {
		Long showId = 1L;

		Show show = org.mockito.Mockito.mock(Show.class);
		Venue venue = org.mockito.Mockito.mock(Venue.class);
		when(show.getId()).thenReturn(showId);
		when(show.getTitle()).thenReturn("뮤지컬");
		when(show.getDescription()).thenReturn("설명");
		when(show.getRuntimeMin()).thenReturn(120);
		when(show.getAgeLimit()).thenReturn(8);
		when(show.getPosterUrl()).thenReturn("https://img/poster.jpg");
		when(show.getNoticeUrl()).thenReturn(null);
		when(show.getStartTime()).thenReturn(LocalDateTime.of(2026, 3, 1, 0, 0));
		when(show.getEndTime()).thenReturn(LocalDateTime.of(2026, 4, 1, 0, 0));
		when(show.getVenue()).thenReturn(venue);
		when(venue.getId()).thenReturn(10L);
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
		when(artistB.getId()).thenReturn(102L);
		when(artistB.getName()).thenReturn("배우B");
		when(artistC.getId()).thenReturn(103L);
		when(artistC.getName()).thenReturn("배우C");

		ShowCasting castOrder2 = org.mockito.Mockito.mock(ShowCasting.class);
		ShowCasting castOrder1 = org.mockito.Mockito.mock(ShowCasting.class);
		ShowCasting castOtherSchedule = org.mockito.Mockito.mock(ShowCasting.class);
		when(castOrder2.getId()).thenReturn(5002L);
		when(castOrder2.getArtist()).thenReturn(artistB);
		when(castOrder2.getRoleName()).thenReturn("조연");
		when(castOrder2.getCastingOrder()).thenReturn(2);
		when(castOrder1.getId()).thenReturn(5001L);
		when(castOrder1.getArtist()).thenReturn(artistA);
		when(castOrder1.getRoleName()).thenReturn("주연");
		when(castOrder1.getCastingOrder()).thenReturn(1);
		when(castOtherSchedule.getId()).thenReturn(5003L);
		when(castOtherSchedule.getArtist()).thenReturn(artistC);
		when(castOtherSchedule.getRoleName()).thenReturn("특별출연");
		when(castOtherSchedule.getCastingOrder()).thenReturn(1);

		ShowScheduleCasting sc1 = org.mockito.Mockito.mock(ShowScheduleCasting.class);
		ShowScheduleCasting sc2 = org.mockito.Mockito.mock(ShowScheduleCasting.class);
		ShowScheduleCasting sc3 = org.mockito.Mockito.mock(ShowScheduleCasting.class);
		when(sc1.getShowSchedule()).thenReturn(schedule1);
		when(sc1.getShowCasting()).thenReturn(castOrder2);
		when(sc2.getShowSchedule()).thenReturn(schedule1);
		when(sc2.getShowCasting()).thenReturn(castOrder1);
		when(sc3.getShowSchedule()).thenReturn(schedule2);
		when(sc3.getShowCasting()).thenReturn(castOtherSchedule);

		ShowSeatGrade seat = org.mockito.Mockito.mock(ShowSeatGrade.class);
		when(seat.getId()).thenReturn(7001L);
		when(seat.getGradeName()).thenReturn("VIP");
		when(seat.getBasePrice()).thenReturn(150000);
		when(seat.getColorCode()).thenReturn("#FFD700");

		when(showRepository.findByIdOrThrow(showId)).thenReturn(show);
		when(showScheduleRepository.findSchedules(showId)).thenReturn(List.of(schedule1, schedule2));
		when(showScheduleCastingRepository.findAllByScheduleIds(List.of(2001L, 2002L)))
			.thenReturn(List.of(sc1, sc2, sc3));
		when(showSeatGradeRepository.findSeatPrices(showId)).thenReturn(List.of(seat));

		ShowResponse.Detail result = showService.getDetail(showId);

		assertEquals(2, result.getSchedules().size());
		assertEquals("OPEN", result.getSchedules().get(0).getStatus());
		assertEquals("CANCELLED", result.getSchedules().get(1).getStatus());

		List<ShowResponse.Casting> firstScheduleCastings = result.getSchedules().get(0).getCastings();
		assertEquals(2, firstScheduleCastings.size());
		assertEquals("배우A", firstScheduleCastings.get(0).getArtistName());
		assertEquals(1, firstScheduleCastings.get(0).getOrder());
		assertEquals("배우B", firstScheduleCastings.get(1).getArtistName());
		assertEquals(2, firstScheduleCastings.get(1).getOrder());

		List<ShowResponse.Casting> secondScheduleCastings = result.getSchedules().get(1).getCastings();
		assertEquals(1, secondScheduleCastings.size());
		assertEquals("배우C", secondScheduleCastings.get(0).getArtistName());

		assertFalse(firstScheduleCastings.get(0).getIsLiked());
		assertFalse(firstScheduleCastings.get(1).getIsLiked());
	}
}
