package com.truve.platform.musical.show.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.musical.show.domain.entity.Show;
import com.truve.platform.musical.show.domain.entity.ShowCasting;
import com.truve.platform.musical.show.domain.entity.ShowSchedule;
import com.truve.platform.musical.show.domain.entity.ShowScheduleCasting;
import com.truve.platform.musical.show.dto.ShowCastingResponse;
import com.truve.platform.musical.show.repository.ShowCastingRepository;
import com.truve.platform.musical.show.repository.ShowRepository;
import com.truve.platform.musical.show.repository.ShowScheduleCastingRepository;
import com.truve.platform.musical.show.repository.ShowScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShowCastingService {

	private final ShowRepository showRepository;
	private final ShowScheduleRepository showScheduleRepository;
	private final ShowCastingRepository showCastingRepository;
	private final ShowScheduleCastingRepository showScheduleCastingRepository;

	@Transactional(readOnly = true)
	public ShowCastingResponse.Detail getCastingSchedules(
		Long showId,
		LocalDate from,
		LocalDate to,
		List<Long> artistIds,
		int page,
		int size
	) {
		Show show = showRepository.findByIdOrThrow(showId);

		LocalDateTime fromTime = from != null ? from.atStartOfDay() : null;
		LocalDateTime toTime = to != null ? to.atTime(LocalTime.MAX) : null;
		List<Long> filterArtistIds = artistIds == null ? List.of() : artistIds;
		boolean artistFilterOff = filterArtistIds.isEmpty();
		List<Long> queryArtistIds = artistFilterOff ? List.of(-1L) : filterArtistIds;

		PageRequest pageable = PageRequest.of(
			page,
			size,
			Sort.by(
				Sort.Order.asc("showTime"),
				Sort.Order.asc("id")
			)
		);
		Page<ShowSchedule> schedulePage = showScheduleRepository.findCastingSchedules(
			showId,
			fromTime,
			toTime,
			artistFilterOff,
			queryArtistIds,
			pageable
		);

		List<ShowCasting> showCastings = showCastingRepository.findAllByShowId(showId);
		List<ShowCastingResponse.Role> roles = buildRoles(showCastings);
		List<ShowCastingResponse.FilterArtist> filterArtists = buildFilterArtists(showCastings);

		List<Long> scheduleIds = schedulePage.getContent().stream()
			.map(ShowSchedule::getId)
			.toList();
		Map<Long, Map<String, ShowCastingResponse.CastArtist>> castsByScheduleId = scheduleIds.isEmpty()
			? Map.of()
			: buildCastsByScheduleId(scheduleIds);

		List<ShowCastingResponse.Row> rows = schedulePage.getContent().stream()
			.map(schedule -> ShowCastingResponse.Row.builder()
				.scheduleId(schedule.getId())
				.showTime(schedule.getShowTime())
				.casts(castsByScheduleId.getOrDefault(schedule.getId(), Map.of()))
				.build())
			.toList();

		return ShowCastingResponse.Detail.builder()
			.showId(show.getId())
			.range(ShowCastingResponse.Range.builder()
				.from(show.getStartTime() != null ? show.getStartTime().toLocalDate() : null)
				.to(show.getEndTime() != null ? show.getEndTime().toLocalDate() : null)
				.build())
			.filters(ShowCastingResponse.Filters.builder()
				.artists(filterArtists)
				.build())
			.roles(roles)
			.page(ShowCastingResponse.Page.builder()
				.currentPage(schedulePage.getNumber())
				.size(schedulePage.getSize())
				.totalElements(schedulePage.getTotalElements())
				.totalPages(schedulePage.getTotalPages())
				.build())
			.rows(rows)
			.build();
	}

	private List<ShowCastingResponse.Role> buildRoles(List<ShowCasting> showCastings) {
		Map<String, ShowCastingResponse.Role> byRoleName = new LinkedHashMap<>();
		showCastings.stream()
			.sorted(Comparator
				.comparing(ShowCasting::getCastingOrder, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(ShowCasting::getId))
			.forEach(casting -> byRoleName.putIfAbsent(
				casting.getRoleName(),
				ShowCastingResponse.Role.builder()
					.roleName(casting.getRoleName())
					.order(casting.getCastingOrder())
					.build()
			));
		return byRoleName.values().stream().toList();
	}

	private List<ShowCastingResponse.FilterArtist> buildFilterArtists(List<ShowCasting> showCastings) {
		Map<Long, ShowCastingResponse.FilterArtist> byArtistId = new LinkedHashMap<>();
		showCastings.forEach(casting -> byArtistId.putIfAbsent(
			casting.getArtist().getId(),
			ShowCastingResponse.FilterArtist.builder()
				.artistId(casting.getArtist().getId())
				.artistName(casting.getArtist().getName())
				.build()
		));
		return byArtistId.values().stream().toList();
	}

	private Map<Long, Map<String, ShowCastingResponse.CastArtist>> buildCastsByScheduleId(List<Long> scheduleIds) {
		List<ShowScheduleCasting> scheduleCastings = showScheduleCastingRepository.findAllByScheduleIds(scheduleIds);
		Map<Long, List<ShowScheduleCasting>> grouped = scheduleCastings.stream()
			.collect(Collectors.groupingBy(sc -> sc.getShowSchedule().getId()));

		Map<Long, Map<String, ShowCastingResponse.CastArtist>> result = new LinkedHashMap<>();
		grouped.forEach((Long scheduleId, List<ShowScheduleCasting> castings) -> {
			Map<String, ShowCastingResponse.CastArtist> casts = castings.stream()
				.sorted(Comparator.comparing(
					(ShowScheduleCasting sc) -> sc.getShowCasting().getCastingOrder(),
					Comparator.nullsLast(Comparator.naturalOrder())
				).thenComparing((ShowScheduleCasting sc) -> sc.getShowCasting().getId()))
				.collect(Collectors.toMap(
					(ShowScheduleCasting sc) -> sc.getShowCasting().getRoleName(),
					(ShowScheduleCasting sc) -> ShowCastingResponse.CastArtist.builder()
						.artistId(sc.getShowCasting().getArtist().getId())
						.artistName(sc.getShowCasting().getArtist().getName())
						.build(),
					(existing, replacement) -> existing,
					LinkedHashMap::new
				));
			result.put(scheduleId, casts);
		});
		return result;
	}
}
