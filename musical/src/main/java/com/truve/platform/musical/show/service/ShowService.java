package com.truve.platform.musical.show.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.musical.seat.domain.entity.Venue;
import com.truve.platform.musical.seat.domain.repository.VenueRepository;
import com.truve.platform.musical.show.domain.entity.Show;
import com.truve.platform.musical.show.domain.entity.ShowCasting;
import com.truve.platform.musical.show.domain.entity.ShowSchedule;
import com.truve.platform.musical.show.domain.entity.ShowSectionGrade;
import com.truve.platform.musical.show.dto.ShowResponse;
import com.truve.platform.musical.show.repository.ShowCastingRepository;
import com.truve.platform.musical.show.repository.ShowRepository;
import com.truve.platform.musical.show.repository.ShowScheduleRepository;
import com.truve.platform.musical.show.repository.ShowSeatGradeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShowService {

	private final ShowRepository showRepository;
	private final ShowCastingRepository showCastingRepository;
	private final VenueRepository venueRepository;
	private final ShowScheduleRepository showScheduleRepository;
	private final ShowSeatGradeRepository showSeatGradeRepository;

	@Transactional(readOnly = true)
	public ShowResponse.Detail getDetail(Long showId) {
		Show show = showRepository.findByIdOrThrow(showId);

		List<ShowSchedule> schedules = showScheduleRepository.findSchedules(showId);
		List<ShowResponse.SimpleSchedule> scheduleResponses = schedules.stream()
			.map(this::toSimpleScheduleResponse)
			.toList();

		List<ShowResponse.Casting> castings = showCastingRepository.findAllByShowId(showId).stream()
			.map(this::toCastingResponse)
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
			.castings(castings)
			.schedules(scheduleResponses)
			.seatGrades(seatGrades)
			.build();
	}

	private ShowResponse.Venue toVenueResponse(Show show) {
		Venue venue = venueRepository.findById(show.getVenueId()).orElse(null);
		return ShowResponse.Venue.builder()
			.venueId(show.getVenueId())
			.name(venue != null ? venue.getName() : null)
			.address(venue != null ? venue.getAddress() : null)
			.build();
	}

	private ShowResponse.SimpleSchedule toSimpleScheduleResponse(ShowSchedule schedule) {
		return ShowResponse.SimpleSchedule.builder()
			.scheduleId(schedule.getId())
			.showTime(schedule.getShowTime())
			.status(schedule.getStatus().name())
			.build();
	}

	private ShowResponse.Casting toCastingResponse(ShowCasting casting) {
		return ShowResponse.Casting.builder()
			.showCastId(casting.getId())
			.artistId(casting.getArtist().getId())
			.artistName(casting.getArtist().getName())
			.profileImageUrl(casting.getArtist().getProfileImageUrl())
			.roleName(casting.getRoleName())
			.order(casting.getCastingOrder())
			// TODO: artist_likes 연동 후 로그인 사용자 기준 값으로 교체
			.isLiked(false)
			.build();
	}

	private ShowResponse.SeatGrade toSeatGradeResponse(ShowSectionGrade seatGrade) {
		return ShowResponse.SeatGrade.builder()
			.showSeatGradeId(seatGrade.getId())
			.gradeName(seatGrade.getGradeName())
			.colorCode(seatGrade.getColorCode())
			.build();
	}
}
