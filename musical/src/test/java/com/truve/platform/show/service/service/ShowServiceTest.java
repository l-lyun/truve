package com.truve.platform.show.service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.seat.domain.entity.Venue;
import com.truve.platform.musical.seat.domain.repository.VenueRepository;
import com.truve.platform.musical.show.domain.constant.ShowScheduleStatus;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.domain.entity.Show;
import com.truve.platform.musical.show.domain.entity.ShowCasting;
import com.truve.platform.musical.show.domain.entity.ShowSchedule;
import com.truve.platform.musical.show.domain.entity.ShowScheduleCasting;
import com.truve.platform.musical.show.domain.entity.ShowSectionGrade;
import com.truve.platform.musical.show.dto.ShowCastingResponse;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.repository.ArtistLikeRepository;
import com.truve.platform.musical.show.repository.ShowCastingRepository;
import com.truve.platform.musical.show.repository.ShowRepository;
import com.truve.platform.musical.show.repository.ShowScheduleCastingRepository;
import com.truve.platform.musical.show.repository.ShowScheduleRepository;
import com.truve.platform.musical.show.repository.ShowSeatGradeRepository;
import com.truve.platform.musical.show.service.ShowCastingService;
import com.truve.platform.musical.show.service.ShowDetailService;

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
	private ShowScheduleCastingRepository showScheduleCastingRepository;
	@Mock
	private ArtistLikeRepository artistLikeRepository;
	@Mock
	private ShowSeatGradeRepository showSeatGradeRepository;
	@Mock
	private S3Service s3Service;

	@InjectMocks
	private ShowDetailService showDetailService;
	@InjectMocks
	private ShowCastingService showCastingService;

	@Test
	@DisplayName("공연 상세는 조회된 공연 전체 캐스팅을 응답한다.")
	void 공연_상세_공연전체_캐스팅_응답_성공() {
		Long showId = 1L;
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

		Show show = org.mockito.Mockito.mock(Show.class);
		Venue venue = org.mockito.Mockito.mock(Venue.class);
		when(show.getId()).thenReturn(showId);
		when(show.getTitle()).thenReturn("뮤지컬");
		when(show.getDescription()).thenReturn("설명");
		when(show.getRuntimeMin()).thenReturn(120);
		when(show.getAgeLimit()).thenReturn(8);
		when(show.getPosterImg()).thenReturn("poster.jpg");
		when(show.getNoticeImg()).thenReturn(List.of("notice.jpg", "detail1.jpg", "detail2.jpg"));
		when(show.getDetailImg()).thenReturn(List.of("content1.jpg", "content2.jpg"));
		when(s3Service.getImageUrl("poster.jpg")).thenReturn("https://img/poster.jpg");
		when(s3Service.getImageUrl("notice.jpg")).thenReturn("https://img/notice.jpg");
		when(s3Service.getImageUrl("detail1.jpg")).thenReturn("https://img/detail1.jpg");
		when(s3Service.getImageUrl("detail2.jpg")).thenReturn("https://img/detail2.jpg");
		when(s3Service.getImageUrl("content1.jpg")).thenReturn("https://img/content1.jpg");
		when(s3Service.getImageUrl("content2.jpg")).thenReturn("https://img/content2.jpg");
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
		when(artistB.getId()).thenReturn(102L);
		when(artistB.getName()).thenReturn("배우B");
		when(artistB.getProfileImg()).thenReturn("artistB.jpg");
		when(s3Service.getImageUrl("artistB.jpg")).thenReturn("https://img.example/artistB.jpg");
		when(artistC.getId()).thenReturn(103L);
		when(artistC.getName()).thenReturn("배우C");

		ShowCasting castOrder1 = org.mockito.Mockito.mock(ShowCasting.class);
		ShowCasting castOrder2 = org.mockito.Mockito.mock(ShowCasting.class);
		ShowCasting castOrderNull = org.mockito.Mockito.mock(ShowCasting.class);
		when(castOrder1.getId()).thenReturn(5001L);
		when(castOrder1.getArtist()).thenReturn(artistA);
		when(castOrder1.getRoleName()).thenReturn("주연");
		when(castOrder1.getCastingOrder()).thenReturn(1);
		when(castOrder1.getProfileImg()).thenReturn("show-casting-artistA.jpg");
		when(s3Service.getImageUrl("show-casting-artistA.jpg")).thenReturn("https://img.example/show-casting-artistA.jpg");
		when(castOrder2.getId()).thenReturn(5002L);
		when(castOrder2.getArtist()).thenReturn(artistB);
		when(castOrder2.getRoleName()).thenReturn("조연");
		when(castOrder2.getCastingOrder()).thenReturn(2);
		when(castOrder2.getProfileImg()).thenReturn(null);
		when(castOrderNull.getId()).thenReturn(5003L);
		when(castOrderNull.getArtist()).thenReturn(artistC);
		when(castOrderNull.getRoleName()).thenReturn("특별출연");
		when(castOrderNull.getCastingOrder()).thenReturn(null);
		when(castOrderNull.getProfileImg()).thenReturn(null);

		ShowSectionGrade seat = org.mockito.Mockito.mock(ShowSectionGrade.class);
		when(seat.getId()).thenReturn(7001L);
		when(seat.getGradeName()).thenReturn("VIP");
		when(seat.getColorCode()).thenReturn("#FFD700");

		when(showRepository.findByIdOrThrow(showId)).thenReturn(show);
		when(venueRepository.findById(10L)).thenReturn(Optional.of(venue));
		when(showScheduleRepository.findSchedules(showId)).thenReturn(List.of(schedule1, schedule2));
		when(showCastingRepository.findAllByShowId(showId))
			.thenReturn(List.of(castOrder1, castOrder2, castOrderNull));
		when(artistLikeRepository.findLikedArtistIds(userId, List.of(101L, 102L, 103L)))
			.thenReturn(List.of(101L));
		when(showSeatGradeRepository.findSeatPrices(showId)).thenReturn(List.of(seat));

		ShowResponse.Detail result = showDetailService.getDetail(showId, userId);

		assertEquals(3, result.getNoticeImgs().size());
		assertEquals("https://img/notice.jpg", result.getNoticeImgs().get(0));
		assertEquals(2, result.getDetailImgs().size());
		assertEquals("https://img/content1.jpg", result.getDetailImgs().get(0));
		assertEquals("2026.03.01 ~ 2026.04.01", result.getDate());
		assertEquals(2, result.getSchedules().size());
		assertEquals("OPEN", result.getSchedules().get(0).getStatus());
		assertEquals("CANCELLED", result.getSchedules().get(1).getStatus());

		List<ShowResponse.Casting> castings = result.getCastings();
		assertEquals(3, castings.size());
		assertEquals("배우A", castings.get(0).getArtistName());
		assertEquals("https://img.example/show-casting-artistA.jpg", castings.get(0).getProfileImageUrl());
		assertEquals(1, castings.get(0).getOrder());
		assertEquals("배우B", castings.get(1).getArtistName());
		assertEquals("https://img.example/artistB.jpg", castings.get(1).getProfileImageUrl());
		assertEquals(2, castings.get(1).getOrder());
		assertEquals("배우C", castings.get(2).getArtistName());
		assertNull(castings.get(2).getProfileImageUrl());
		assertNull(castings.get(2).getOrder());

		assertTrue(castings.get(0).getIsLiked());
		assertFalse(castings.get(1).getIsLiked());
		assertFalse(castings.get(2).getIsLiked());
	}

	@Test
	@DisplayName("캐스팅 일정 조회는 페이지/역할/필터/행 데이터를 응답한다.")
	void 캐스팅_일정_조회_성공() {
		Long showId = 1L;
		Show show = org.mockito.Mockito.mock(Show.class);
		when(show.getId()).thenReturn(showId);

		ShowSchedule schedule1 = org.mockito.Mockito.mock(ShowSchedule.class);
		when(schedule1.getId()).thenReturn(101L);
		when(schedule1.getShowTime()).thenReturn(LocalDateTime.of(2026, 1, 2, 19, 0));

		Artist artistCharlie = org.mockito.Mockito.mock(Artist.class);
		when(artistCharlie.getId()).thenReturn(1L);
		when(artistCharlie.getName()).thenReturn("김호영");

		Artist artistLola = org.mockito.Mockito.mock(Artist.class);
		when(artistLola.getId()).thenReturn(10L);
		when(artistLola.getName()).thenReturn("강홍석");

		ShowCasting charlieCasting = org.mockito.Mockito.mock(ShowCasting.class);
		when(charlieCasting.getRoleName()).thenReturn("찰리");
		when(charlieCasting.getCastingOrder()).thenReturn(1);
		when(charlieCasting.getArtist()).thenReturn(artistCharlie);

		ShowCasting lolaCasting = org.mockito.Mockito.mock(ShowCasting.class);
		when(lolaCasting.getRoleName()).thenReturn("롤라");
		when(lolaCasting.getCastingOrder()).thenReturn(2);
		when(lolaCasting.getArtist()).thenReturn(artistLola);

		ShowScheduleCasting sc1 = org.mockito.Mockito.mock(ShowScheduleCasting.class);
		when(sc1.getShowSchedule()).thenReturn(schedule1);
		when(sc1.getShowCasting()).thenReturn(charlieCasting);

		ShowScheduleCasting sc2 = org.mockito.Mockito.mock(ShowScheduleCasting.class);
		when(sc2.getShowSchedule()).thenReturn(schedule1);
		when(sc2.getShowCasting()).thenReturn(lolaCasting);

		when(showRepository.findByIdOrThrow(showId)).thenReturn(show);
		when(showScheduleRepository.findCastingSchedules(
			org.mockito.ArgumentMatchers.eq(showId),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.eq(true),
			org.mockito.ArgumentMatchers.anyList(),
			org.mockito.ArgumentMatchers.any(PageRequest.class)
		)).thenReturn(new PageImpl<>(List.of(schedule1), PageRequest.of(0, 50), 1));
		when(showCastingRepository.findAllByShowId(showId)).thenReturn(List.of(charlieCasting, lolaCasting));
		when(showScheduleCastingRepository.findAllByScheduleIds(List.of(101L))).thenReturn(List.of(sc1, sc2));

		ShowCastingResponse.Detail result = showCastingService.getCastingSchedules(
			showId,
			LocalDate.of(2025, 12, 17),
			LocalDate.of(2026, 3, 29),
			List.of(),
			0,
			50
		);

		assertEquals(showId, result.getShowId());
		assertEquals(LocalDate.of(2025, 12, 17), result.getRange().getFrom());
		assertEquals(LocalDate.of(2026, 3, 29), result.getRange().getTo());
		assertEquals(2, result.getRoles().size());
		assertEquals("찰리", result.getRoles().get(0).getRoleName());
		assertEquals(1, result.getRoles().get(0).getOrder());
		assertEquals(1, result.getRows().size());
		assertEquals(101L, result.getRows().get(0).getScheduleId());
		assertEquals("01/02(금)", result.getRows().get(0).getShowDateLabel());
		assertEquals("오후 7:00", result.getRows().get(0).getShowTimeLabel());
		assertEquals("김호영", result.getRows().get(0).getCasts().get("찰리").getArtistName());
		assertEquals("강홍석", result.getRows().get(0).getCasts().get("롤라").getArtistName());
		assertEquals(0, result.getPage().getCurrentPage());
		assertEquals(50, result.getPage().getSize());
		assertEquals(1, result.getPage().getTotalElements());
		assertEquals(1, result.getPage().getTotalPages());
	}

	@Test
	@DisplayName("캐스팅 일정 조회 range는 요청 from/to를 우선 반환한다.")
	void 캐스팅_일정_조회_range는_요청값_우선() {
		Long showId = 1L;
		Show show = org.mockito.Mockito.mock(Show.class);
		when(show.getId()).thenReturn(showId);

		LocalDate requestFrom = LocalDate.of(2026, 3, 10);
		LocalDate requestTo = LocalDate.of(2026, 3, 12);

		when(showRepository.findByIdOrThrow(showId)).thenReturn(show);
		when(showScheduleRepository.findCastingSchedules(
			org.mockito.ArgumentMatchers.eq(showId),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.eq(true),
			org.mockito.ArgumentMatchers.anyList(),
			org.mockito.ArgumentMatchers.any(PageRequest.class)
		)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
		when(showCastingRepository.findAllByShowId(showId)).thenReturn(List.of());

		ShowCastingResponse.Detail result = showCastingService.getCastingSchedules(
			showId,
			requestFrom,
			requestTo,
			List.of(),
			0,
			50
		);

		assertEquals(requestFrom, result.getRange().getFrom());
		assertEquals(requestTo, result.getRange().getTo());
	}

	@Test
	@DisplayName("캐스팅 미확정 역할은 미정으로 채워 응답한다.")
	void 캐스팅_일정_조회_미확정_역할은_미정으로_응답() {
		Long showId = 1L;
		Show show = org.mockito.Mockito.mock(Show.class);
		when(show.getId()).thenReturn(showId);

		ShowSchedule schedule1 = org.mockito.Mockito.mock(ShowSchedule.class);
		when(schedule1.getId()).thenReturn(101L);
		when(schedule1.getShowTime()).thenReturn(LocalDateTime.of(2026, 1, 2, 19, 0));

		Artist artistCharlie = org.mockito.Mockito.mock(Artist.class);
		when(artistCharlie.getId()).thenReturn(1L);
		when(artistCharlie.getName()).thenReturn("김호영");

		Artist artistLola = org.mockito.Mockito.mock(Artist.class);
		when(artistLola.getId()).thenReturn(10L);
		when(artistLola.getName()).thenReturn("강홍석");

		Artist artistLauren = org.mockito.Mockito.mock(Artist.class);
		when(artistLauren.getId()).thenReturn(20L);
		when(artistLauren.getName()).thenReturn("허윤슬");

		ShowCasting charlieCasting = org.mockito.Mockito.mock(ShowCasting.class);
		when(charlieCasting.getId()).thenReturn(1001L);
		when(charlieCasting.getRoleName()).thenReturn("찰리");
		when(charlieCasting.getCastingOrder()).thenReturn(1);
		when(charlieCasting.getArtist()).thenReturn(artistCharlie);

		ShowCasting lolaCasting = org.mockito.Mockito.mock(ShowCasting.class);
		when(lolaCasting.getId()).thenReturn(1002L);
		when(lolaCasting.getRoleName()).thenReturn("롤라");
		when(lolaCasting.getCastingOrder()).thenReturn(2);
		when(lolaCasting.getArtist()).thenReturn(artistLola);

		ShowCasting laurenCasting = org.mockito.Mockito.mock(ShowCasting.class);
		when(laurenCasting.getId()).thenReturn(1003L);
		when(laurenCasting.getRoleName()).thenReturn("로렌");
		when(laurenCasting.getCastingOrder()).thenReturn(3);
		when(laurenCasting.getArtist()).thenReturn(artistLauren);

		ShowScheduleCasting sc1 = org.mockito.Mockito.mock(ShowScheduleCasting.class);
		when(sc1.getShowSchedule()).thenReturn(schedule1);
		when(sc1.getShowCasting()).thenReturn(charlieCasting);

		ShowScheduleCasting sc2 = org.mockito.Mockito.mock(ShowScheduleCasting.class);
		when(sc2.getShowSchedule()).thenReturn(schedule1);
		when(sc2.getShowCasting()).thenReturn(lolaCasting);

		when(showRepository.findByIdOrThrow(showId)).thenReturn(show);
		when(showScheduleRepository.findCastingSchedules(
			org.mockito.ArgumentMatchers.eq(showId),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.eq(true),
			org.mockito.ArgumentMatchers.anyList(),
			org.mockito.ArgumentMatchers.any(PageRequest.class)
		)).thenReturn(new PageImpl<>(List.of(schedule1), PageRequest.of(0, 50), 1));
		when(showCastingRepository.findAllByShowId(showId))
			.thenReturn(List.of(charlieCasting, lolaCasting, laurenCasting));
		when(showScheduleCastingRepository.findAllByScheduleIds(List.of(101L))).thenReturn(List.of(sc1, sc2));

		ShowCastingResponse.Detail result = showCastingService.getCastingSchedules(
			showId,
			LocalDate.of(2025, 12, 17),
			LocalDate.of(2026, 3, 29),
			List.of(),
			0,
			50
		);

		assertEquals("김호영", result.getRows().get(0).getCasts().get("찰리").getArtistName());
		assertEquals("강홍석", result.getRows().get(0).getCasts().get("롤라").getArtistName());
		assertNull(result.getRows().get(0).getCasts().get("로렌").getArtistId());
		assertEquals("미정", result.getRows().get(0).getCasts().get("로렌").getArtistName());
	}

	@Test
	@DisplayName("캐스팅 일정 조회는 기간이 없으면 오늘부터 공연 종료일까지 조회한다.")
	void 캐스팅_일정_조회_기본기간은_오늘부터_공연종료일() {
		Long showId = 1L;
		LocalDate today = LocalDate.now();
		LocalDate showEndDate = today.plusDays(7);

		Show show = org.mockito.Mockito.mock(Show.class);
		when(show.getId()).thenReturn(showId);
		when(show.getEndTime()).thenReturn(showEndDate.atStartOfDay());

		when(showRepository.findByIdOrThrow(showId)).thenReturn(show);
		when(showScheduleRepository.findCastingSchedules(
			eq(showId),
			eq(today.atStartOfDay()),
			eq(showEndDate.atTime(23, 59, 59, 999999999)),
			eq(true),
			any(),
			any(PageRequest.class)
		)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
		when(showCastingRepository.findAllByShowId(showId)).thenReturn(List.of());

		ShowCastingResponse.Detail result = showCastingService.getCastingSchedules(
			showId,
			null,
			null,
			List.of(),
			0,
			50
		);

		assertEquals(today, result.getRange().getFrom());
		assertEquals(showEndDate, result.getRange().getTo());
		verify(showScheduleRepository).findCastingSchedules(
			eq(showId),
			eq(today.atStartOfDay()),
			eq(showEndDate.atTime(23, 59, 59, 999999999)),
			eq(true),
			any(),
			any(PageRequest.class)
		);
	}
}
