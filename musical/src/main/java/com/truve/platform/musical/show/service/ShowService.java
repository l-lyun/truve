package com.truve.platform.musical.show.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.musical.show.domain.entity.Show;
import com.truve.platform.musical.show.domain.entity.ShowCasting;
import com.truve.platform.musical.show.domain.entity.ShowSchedule;
import com.truve.platform.musical.show.domain.entity.ShowScheduleCasting;
import com.truve.platform.musical.pricing.domain.entity.ShowSeatGrade;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.repository.ShowRepository;
import com.truve.platform.musical.show.repository.ShowScheduleRepository;
import com.truve.platform.musical.show.repository.ShowScheduleCastingRepository;
import com.truve.platform.musical.pricing.domain.repository.ShowSeatGradeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShowService {

	private final ShowRepository showRepository;
	private final ShowScheduleRepository showScheduleRepository;
	private final ShowScheduleCastingRepository showScheduleCastingRepository;
	private final ShowSeatGradeRepository showSeatGradeRepository;

	@Transactional(readOnly = true)
	public ShowResponse.Detail getDetail(Long showId) {
		Show show = showRepository.findByIdOrThrow(showId);

		List<ShowSchedule> schedules = showScheduleRepository.findSchedules(showId);
		List<Long> scheduleIds = schedules.stream()
			.map(ShowSchedule::getId)
			.toList();

		// 회차별 캐스팅 매핑을 한 번에 조회해 쿼리 수를 최소화한다.
		List<ShowScheduleCasting> scheduleCastings = scheduleIds.isEmpty()
			? List.of()
			: showScheduleCastingRepository.findAllByScheduleIds(scheduleIds);

		// 회차 ID를 key로 캐스팅을 묶어 schedules[].castings[] 형태로 조립한다.
		Map<Long, List<ShowResponse.Casting>> castingsByScheduleId = scheduleIds.isEmpty()
			? Map.of()
			: scheduleCastings.stream()
				.sorted(Comparator.comparing(
					sc -> sc.getShowCasting().getCastingOrder(),
					Comparator.nullsLast(Comparator.naturalOrder())
				))
				.collect(Collectors.groupingBy(
					sc -> sc.getShowSchedule().getId(),
					Collectors.mapping(sc -> toCastingResponse(sc.getShowCasting()),
						Collectors.toList())
				));

		List<ShowResponse.Schedule> scheduleResponses = schedules.stream()
			.map(schedule -> toScheduleResponse(schedule, castingsByScheduleId))
			.toList();

		List<ShowResponse.SeatGrade> seatGrades = showSeatGradeRepository
			.findSeatPrices(showId).stream()
			.map(this::toSeatGradeResponse)
			.toList();

		return ShowResponse.Detail.builder()
			.showId(show.getId())
			.title(show.getTitle())
			.description(show.getDescription())
			.runtimeMin(show.getRuntimeMin())
			.ageLimit(show.getAgeLimit())
			.posterUrl(show.getPosterUrl())
			.noticeUrl(show.getNoticeUrl())
			.startTime(show.getStartTime())
			.endTime(show.getEndTime())
			.venue(toVenueResponse(show))
			.schedules(scheduleResponses)
			.seatGrades(seatGrades)
			.build();
	}

	private ShowResponse.Venue toVenueResponse(Show show) {
		return ShowResponse.Venue.builder()
			.venueId(show.getVenue().getId())
			.name(show.getVenue().getName())
			.address(show.getVenue().getAddress())
			.build();
	}

	private ShowResponse.Schedule toScheduleResponse(
		ShowSchedule schedule,
		Map<Long, List<ShowResponse.Casting>> castingsByScheduleId
	) {
		List<ShowResponse.Casting> castings = castingsByScheduleId.getOrDefault(schedule.getId(), List.of());

		return ShowResponse.Schedule.builder()
			.scheduleId(schedule.getId())
			.showTime(schedule.getShowTime())
			.status(schedule.getStatus().name())
			.castings(castings)
			.build();
	}

	private ShowResponse.Casting toCastingResponse(ShowCasting casting) {
		return ShowResponse.Casting.builder()
			.showCastId(casting.getId())
			.artistId(casting.getArtist().getId())
			.artistName(casting.getArtist().getName())
			.roleName(casting.getRoleName())
			.order(casting.getCastingOrder())
			// TODO: artist_likes 연동 후 로그인 사용자 기준 값으로 교체
			.isLiked(false)
			.build();
	}

	private ShowResponse.SeatGrade toSeatGradeResponse(ShowSeatGrade seatGrade) {
		return ShowResponse.SeatGrade.builder()
			.showSeatGradeId(seatGrade.getId())
			.gradeName(seatGrade.getGradeName())
			.basePrice(seatGrade.getBasePrice())
			.colorCode(seatGrade.getColorCode())
			.build();
	}
}
