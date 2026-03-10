package com.truve.platform.musical.show.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.seat.domain.entity.Venue;
import com.truve.platform.musical.seat.domain.repository.VenueRepository;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShowDetailService {

	private final ShowRepository showRepository;
	private final ShowCastingRepository showCastingRepository;
	private final VenueRepository venueRepository;
	private final ShowScheduleRepository showScheduleRepository;
	private final ArtistLikeRepository artistLikeRepository;
	private final ShowSeatGradeRepository showSeatGradeRepository;
	private final S3Service s3Service;

	@Transactional(readOnly = true)
	public ShowResponse.Detail getDetail(Long showId, Long userId) {
		Show show = showRepository.findByIdOrThrow(showId);

		List<ShowSchedule> schedules = showScheduleRepository.findSchedules(showId);
		List<ShowResponse.SimpleSchedule> scheduleResponses = schedules.stream()
			.map(this::toSimpleScheduleResponse)
			.toList();

		List<ShowCasting> showCastings = showCastingRepository.findAllByShowId(showId);
		Set<Long> likedArtistIds = findLikedArtistIds(userId, showCastings);

		List<ShowResponse.Casting> castings = showCastings.stream()
			.map(casting -> toCastingResponse(
				casting,
				likedArtistIds.contains(casting.getArtist().getId())
			))
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
			.posterUrl(toImageUrl(show.getPosterImg()))
			.noticeUrl(toImageUrl(show.getNoticeImg()))
			.startTime(show.getStartTime())
			.endTime(show.getEndTime())
			.venue(toVenueResponse(show))
			.castings(castings)
			.schedules(scheduleResponses)
			.seatGrades(seatGrades)
			.build();
	}

	private Set<Long> findLikedArtistIds(Long userId, List<ShowCasting> showCastings) {
		if (userId == null || showCastings.isEmpty()) {
			return Set.of();
		}

		List<Long> artistIds = showCastings.stream()
			.map(casting -> casting.getArtist().getId())
			.distinct()
			.toList();

		if (artistIds.isEmpty()) {
			return Set.of();
		}

		return Set.copyOf(artistLikeRepository.findLikedArtistIds(userId, artistIds));
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

	private ShowResponse.Casting toCastingResponse(ShowCasting casting, boolean isLiked) {
		return ShowResponse.Casting.builder()
			.showCastId(casting.getId())
			.artistId(casting.getArtist().getId())
			.artistName(casting.getArtist().getName())
			.profileImageUrl(toImageUrl(casting.getArtist().getProfileImg()))
			.roleName(casting.getRoleName())
			.order(casting.getCastingOrder())
			.isLiked(isLiked)
			.build();
	}

	private String toImageUrl(String fileName) {
		if (!StringUtils.hasText(fileName)) {
			return null;
		}
		return s3Service.getImageUrl(fileName);
	}

	private ShowResponse.SeatGrade toSeatGradeResponse(ShowSectionGrade seatGrade) {
		return ShowResponse.SeatGrade.builder()
			.showSeatGradeId(seatGrade.getId())
			.gradeName(seatGrade.getGradeName())
			.colorCode(seatGrade.getColorCode())
			.price(seatGrade.getPrice())
			.build();
	}
}
