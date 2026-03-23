package com.truve.platform.musical.show.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.show.dto.SearchResponse;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.repository.ShowCastingRepository;
import com.truve.platform.musical.show.repository.ShowRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SearchService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
	private static final String DEFAULT_DATE = "기간 미정";
	private static final String DEFAULT_SHOW_TITLE = "작품명 미정";
	private static final String DEFAULT_VENUE_NAME = "공연장 정보 없음";
	private static final String DEFAULT_APPEARANCE_INFO = "출연 정보 없음";
	private static final int MAX_APPEARANCE_SHOWS = 2;

	private final ShowRepository showRepository;
	private final ArtistRepository artistRepository;
	private final ShowCastingRepository showCastingRepository;
	private final S3Service s3Service;

	@Transactional(readOnly = true)
	public SearchResponse.SearchResult search(
		String keyword,
		int artistOffset,
		int artistLimit,
		int showOffset,
		int showLimit
	) {
		String trimmedKeyword = trimKeyword(keyword);
		if (!StringUtils.hasText(trimmedKeyword)) {
			return SearchResponse.SearchResult.empty(trimmedKeyword);
		}

		LocalDateTime now = LocalDate.now().atStartOfDay();

		List<ShowRepository.ShowSearchProjection> allShowProjections = showRepository.searchShows(trimmedKeyword, now);
		List<ArtistRepository.ArtistSearchProjection> allArtistProjections = artistRepository.searchArtists(trimmedKeyword);
		List<ShowCastingRepository.ArtistAppearanceProjection> allAppearances = findAppearances(allArtistProjections);

		int totalArtistCount = allArtistProjections.size();
		List<ArtistRepository.ArtistSearchProjection> paginatedArtistProjections = allArtistProjections.stream()
			.skip(artistOffset)
			.limit(artistLimit)
			.toList();

		Set<Long> paginatedArtistIds = paginatedArtistProjections.stream()
			.map(ArtistRepository.ArtistSearchProjection::getArtistId)
			.collect(Collectors.toSet());

		List<ShowCastingRepository.ArtistAppearanceProjection> paginatedAppearances = allAppearances.stream()
			.filter(app -> paginatedArtistIds.contains(app.getArtistId()))
			.toList();

		Map<Long, String> appearanceInfoByArtistId = buildAppearanceInfoByArtistId(paginatedArtistProjections, paginatedAppearances);

		LinkedHashMap<Long, SearchResponse.ShowSummary> allShowsMap = buildShowsMap(allShowProjections, allAppearances, now);
		int totalShowCount = allShowsMap.size();

		List<SearchResponse.ShowSummary> paginatedShows = allShowsMap.values().stream()
			.skip(showOffset)
			.limit(showLimit)
			.toList();

		List<SearchResponse.ArtistSummary> artists = paginatedArtistProjections.stream()
			.map(artist -> SearchResponse.ArtistSummary.of(
				artist.getArtistId(),
				artist.getArtistName(),
				toImageUrl(artist.getProfileImg()),
				appearanceInfoByArtistId.getOrDefault(artist.getArtistId(), DEFAULT_APPEARANCE_INFO)
			))
			.toList();

		boolean hasMoreArtists = (artistOffset + artists.size()) < totalArtistCount;
		boolean hasMoreShows = (showOffset + paginatedShows.size()) < totalShowCount;

		return SearchResponse.SearchResult.of(
			trimmedKeyword,
			artists,
			paginatedShows,
			totalArtistCount,
			totalShowCount,
			hasMoreArtists,
			hasMoreShows
		);
	}

	private List<ShowCastingRepository.ArtistAppearanceProjection> findAppearances(
		List<ArtistRepository.ArtistSearchProjection> artists
	) {
		if (artists == null || artists.isEmpty()) {
			return List.of();
		}

		List<Long> artistIds = artists.stream()
			.map(ArtistRepository.ArtistSearchProjection::getArtistId)
			.toList();
		return showCastingRepository.findAppearanceInfoByArtistIds(artistIds);
	}

	private Map<Long, String> buildAppearanceInfoByArtistId(
		List<ArtistRepository.ArtistSearchProjection> artists,
		List<ShowCastingRepository.ArtistAppearanceProjection> appearances
	) {
		if (artists.isEmpty()) {
			return Collections.emptyMap();
		}

		List<Long> artistIds = artists.stream()
			.map(ArtistRepository.ArtistSearchProjection::getArtistId)
			.toList();
		Map<Long, String> result = new LinkedHashMap<>();
		artistIds.forEach(artistId -> result.put(artistId, DEFAULT_APPEARANCE_INFO));

		if (appearances.isEmpty()) {
			return result;
		}

		Map<Long, LinkedHashMap<Long, ShowCastingRepository.ArtistAppearanceProjection>> byArtist = new LinkedHashMap<>();
		for (ShowCastingRepository.ArtistAppearanceProjection appearance : appearances) {
			byArtist.computeIfAbsent(appearance.getArtistId(), ignored -> new LinkedHashMap<>())
				.putIfAbsent(appearance.getShowId(), appearance);
		}

		for (Long artistId : artistIds) {
			Map<Long, ShowCastingRepository.ArtistAppearanceProjection> showMap = byArtist.get(artistId);
			if (showMap == null || showMap.isEmpty()) {
				continue;
			}

			String labels = showMap.values().stream()
				.limit(MAX_APPEARANCE_SHOWS)
				.map(this::toAppearanceLabel)
				.collect(Collectors.joining(", "));
			String info = labels.isEmpty() ? DEFAULT_APPEARANCE_INFO : "출연: " + labels;
			result.put(artistId, info);
		}
		return result;
	}

	private LinkedHashMap<Long, SearchResponse.ShowSummary> buildShowsMap(
		List<ShowRepository.ShowSearchProjection> showProjections,
		List<ShowCastingRepository.ArtistAppearanceProjection> appearances,
		LocalDateTime now
	) {
		LinkedHashMap<Long, SearchResponse.ShowSummary> dedup = new LinkedHashMap<>();

		showProjections.forEach(show -> dedup.put(
			show.getShowId(),
			toShowSummary(show.getShowId(), show.getPosterImg(), show.getTitle(),
				show.getVenueName(), show.getStartTime(), show.getEndTime())
		));

		if (!appearances.isEmpty()) {
			appearances.forEach(appearance -> {
				if (isEnded(appearance.getEndTime(), now)) {
					return;
				}
				dedup.putIfAbsent(
					appearance.getShowId(),
					toShowSummary(appearance.getShowId(), appearance.getPosterImg(), appearance.getShowTitle(),
						appearance.getVenueName(), appearance.getStartTime(), appearance.getEndTime())
				);
			});
		}

		return dedup;
	}

	private SearchResponse.ShowSummary toShowSummary(
		Long showId,
		String posterImg,
		String title,
		String venueName,
		LocalDateTime startTime,
		LocalDateTime endTime
	) {
		return SearchResponse.ShowSummary.of(
			showId,
			toImageUrl(posterImg),
			withDefaultShowTitle(title),
			withDefaultVenueName(venueName),
			toDateRange(startTime, endTime)
		);
	}

	private String toAppearanceLabel(ShowCastingRepository.ArtistAppearanceProjection appearance) {
		String showTitle = withDefaultShowTitle(appearance.getShowTitle());
		String year = appearance.getStartTime() != null ? String.valueOf(appearance.getStartTime().getYear()) : "미정";
		return "뮤지컬 <" + showTitle + ">(" + year + ")";
	}

	private String withDefaultShowTitle(String showTitle) {
		return StringUtils.hasText(showTitle) ? showTitle : DEFAULT_SHOW_TITLE;
	}

	private String trimKeyword(String keyword) {
		return keyword == null ? "" : keyword.trim();
	}

	private String toImageUrl(String imageKey) {
		if (!StringUtils.hasText(imageKey)) {
			return null;
		}
		return s3Service.getImageUrl(imageKey);
	}

	private String toDateRange(LocalDateTime startTime, LocalDateTime endTime) {
		if (startTime == null || endTime == null) {
			return DEFAULT_DATE;
		}
		return DATE_FORMATTER.format(startTime) + " - " + DATE_FORMATTER.format(endTime);
	}

	private String withDefaultVenueName(String venueName) {
		return StringUtils.hasText(venueName) ? venueName : DEFAULT_VENUE_NAME;
	}

	private boolean isEnded(LocalDateTime endTime, LocalDateTime now) {
		return endTime != null && endTime.isBefore(now);
	}

}