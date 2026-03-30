package com.truve.platform.musical.show.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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
	private static final DateTimeFormatter DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
	private static final String DEFAULT_DATE = "기간 미정";

	private final ShowRepository showRepository;
	private final ShowCastingRepository showCastingRepository;
	private final VenueRepository venueRepository;
	private final ShowScheduleRepository showScheduleRepository;
	private final ArtistLikeRepository artistLikeRepository;
	private final ShowSeatGradeRepository showSeatGradeRepository;
	private final S3Service s3Service;

	@Transactional(readOnly = true)
	public ShowResponse.Detail getDetail(Long showId, UUID userId) {
		Show show = showRepository.findByIdOrThrow(showId);

		List<ShowSchedule> schedules = showScheduleRepository.findSchedules(showId);
		List<ShowResponse.SimpleSchedule> scheduleResponses = schedules.stream()
			.map(this::toSimpleScheduleResponse)
			.toList();

		List<ShowCasting> showCastings = showCastingRepository.findAllByShowId(showId);
		List<ShowCasting> uniqueShowCastings = deduplicateShowCastings(showCastings);
		Set<Long> likedArtistIds = findLikedArtistIds(userId, uniqueShowCastings);

		List<ShowResponse.Casting> castings = uniqueShowCastings.stream()
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
			.noticeImgs(toImageUrls(show.getNoticeImg()))
			.detailImgs(toImageUrls(show.getDetailImg()))
			.date(toDateRange(show.getStartTime(), show.getEndTime()))
			.startTime(show.getStartTime())
			.endTime(show.getEndTime())
			.venue(toVenueResponse(show))
			.castings(castings)
			.schedules(scheduleResponses)
			.seatGrades(seatGrades)
			.build();
	}

	private List<ShowCasting> deduplicateShowCastings(List<ShowCasting> showCastings) {
		Map<String, ShowCasting> uniqueShowCastings = new LinkedHashMap<>();

		for (ShowCasting showCasting : showCastings) {
			uniqueShowCastings.putIfAbsent(buildShowCastingKey(showCasting), showCasting);
		}

		return List.copyOf(uniqueShowCastings.values());
	}

	private String buildShowCastingKey(ShowCasting showCasting) {
		return showCasting.getArtist().getId() + ":" + showCasting.getRoleName();
	}

	private Set<Long> findLikedArtistIds(UUID userId, List<ShowCasting> showCastings) {
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
			.profileImageUrl(toImageUrl(chooseProfileImgKey(casting)))
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

	private List<String> toImageUrls(List<String> fileNames) {
		if (fileNames == null || fileNames.isEmpty()) {
			return Collections.emptyList();
		}

		return fileNames.stream()
			.map(this::toImageUrl)
			.filter(Objects::nonNull)
			.toList();
	}

	private ShowResponse.SeatGrade toSeatGradeResponse(ShowSectionGrade seatGrade) {
		return ShowResponse.SeatGrade.builder()
			.showSeatGradeId(seatGrade.getId())
			.gradeName(seatGrade.getGradeName())
			.colorCode(seatGrade.getColorCode())
			.price(seatGrade.getPrice())
			.build();
	}

	private String chooseProfileImgKey(ShowCasting casting) {
		if (StringUtils.hasText(casting.getProfileImg())) {
			return casting.getProfileImg();
		}
		return casting.getArtist().getProfileImg();
	}

	private String toDateRange(LocalDateTime startTime, LocalDateTime endTime) {
		if (startTime == null || endTime == null) {
			return DEFAULT_DATE;
		}
		return startTime.format(DATE_LABEL_FORMATTER) + " ~ " + endTime.format(DATE_LABEL_FORMATTER);
	}
}
