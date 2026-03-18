package com.truve.platform.musical.show.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.common.response.Paging;
import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.seat.domain.entity.Venue;
import com.truve.platform.musical.seat.domain.repository.VenueRepository;
import com.truve.platform.musical.show.domain.constant.HomeShowOrder;
import com.truve.platform.musical.show.domain.constant.HomeRegion;
import com.truve.platform.musical.show.domain.entity.HomeBanner;
import com.truve.platform.musical.show.domain.entity.Show;
import com.truve.platform.musical.show.dto.HomeResponse;
import com.truve.platform.musical.show.repository.HomeBannerRepository;
import com.truve.platform.musical.show.repository.ShowRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HomeService {

	private static final int MAX_BANNERS = 5;
	private static final HomeShowOrder DEFAULT_ORDER = HomeShowOrder.DAILY_BOOKING;
	private static final HomeRegion DEFAULT_REGION = HomeRegion.ALL;
	private static final DateTimeFormatter BANNER_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
	private static final String DEFAULT_SHOW_TITLE = "공연 제목 없음";
	private static final String DEFAULT_VENUE_NAME = "공연장 정보 없음";
	private static final String DEFAULT_DATE = "기간 미정";

	private final HomeBannerRepository homeBannerRepository;
	private final ShowRepository showRepository;
	private final VenueRepository venueRepository;
	private final S3Service s3Service;

	@Transactional(readOnly = true)
	public HomeResponse.ShowList getHomeShows(HomeShowOrder order, HomeRegion region, Paging paging) {
		HomeShowOrder normalizedOrder = order != null ? order : DEFAULT_ORDER;
		HomeRegion normalizedRegion = region != null ? region : DEFAULT_REGION;
		Pageable pageable = paging.toPageable();
		Page<Show> showPage = findHomeShowsByOrder(normalizedOrder, normalizedRegion, pageable);
		List<Show> pageShows = showPage.getContent();
		Map<Long, Venue> venuesById = getVenuesByShowList(pageShows);

		List<HomeResponse.ShowSummary> summaries = pageShows.stream()
			.map(show -> toShowSummary(show, venuesById))
			.toList();

		return toShowListResponse(showPage, summaries);
	}

	private Page<Show> findHomeShowsByOrder(HomeShowOrder order, HomeRegion region, Pageable pageable) {
		String regionKeyword = region.getKeyword();
		LocalDateTime now = LocalDate.now().atStartOfDay();
		return switch (order) {
			case WEEKLY_BOOKING -> showRepository.findHomeShowsOrderByWeeklyRank(regionKeyword, now, pageable);
			case ENDING_SOON -> showRepository.findHomeShowsOrderByEndingSoon(regionKeyword, now, pageable);
			case MOST_REVIEWED -> showRepository.findHomeShowsOrderByReviewCount(regionKeyword, now, pageable);
			case DAILY_BOOKING -> showRepository.findHomeShowsOrderByDailyRank(regionKeyword, now, pageable);
		};
	}

	@Transactional(readOnly = true)
	public HomeResponse.BannerList getHomeBanners() {
		LocalDate today = LocalDate.now();
		List<HomeBanner> banners = homeBannerRepository.findActiveBanners(PageRequest.of(0, MAX_BANNERS));
		Map<Long, Show> showsById = getShowsById(banners);
		Map<Long, String> venueNamesById = getVenueNamesById(showsById);

		List<HomeResponse.Banner> responses = banners.stream()
			.filter(banner -> isVisibleBanner(banner, showsById, today))
			.map(banner -> toBannerResponse(banner, showsById, venueNamesById))
			.toList();

		return HomeResponse.BannerList.builder()
			.banners(responses)
			.build();
	}

	private HomeResponse.Banner toBannerResponse(
		HomeBanner banner,
		Map<Long, Show> showsById,
		Map<Long, String> venueNamesById
	) {
		Show show = showsById.get(banner.getShowId());
		Long venueId = show != null ? show.getVenueId() : null;
		return HomeResponse.Banner.builder()
			.bannerId(banner.getId())
			.showId(banner.getShowId())
			.showTitle(show != null ? show.getTitle() : DEFAULT_SHOW_TITLE)
			.venueName(venueId != null ? venueNamesById.getOrDefault(venueId, DEFAULT_VENUE_NAME) : DEFAULT_VENUE_NAME)
			.date(toDateRange(show))
			.posterUrl(toImageUrl(banner.getImageKey()))
			.displayOrder(banner.getDisplayOrder())
			.build();
	}

	private String toDateRange(Show show) {
		if (show == null || show.getStartTime() == null || show.getEndTime() == null) {
			return DEFAULT_DATE;
		}
		return BANNER_DATE_FORMATTER.format(show.getStartTime()) + " - "
			+ BANNER_DATE_FORMATTER.format(show.getEndTime());
	}

	private Map<Long, Show> getShowsById(List<HomeBanner> banners) {
		if (banners.isEmpty()) {
			return Collections.emptyMap();
		}
		List<Long> showIds = banners.stream()
			.map(HomeBanner::getShowId)
			.distinct()
			.toList();
		return showRepository.findAllById(showIds).stream()
			.collect(Collectors.toMap(Show::getId, show -> show, (left, right) -> left, LinkedHashMap::new));
	}

	private Map<Long, String> getVenueNamesById(Map<Long, Show> showsById) {
		if (showsById.isEmpty()) {
			return Collections.emptyMap();
		}
		List<Long> venueIds = showsById.values().stream()
			.map(Show::getVenueId)
			.distinct()
			.toList();
		return getVenuesByIds(venueIds).stream()
			.collect(Collectors.toMap(Venue::getId, Venue::getName, (left, right) -> left, LinkedHashMap::new));
	}

	private Map<Long, Venue> getVenuesByShowList(List<Show> shows) {
		if (shows.isEmpty()) {
			return Collections.emptyMap();
		}
		List<Long> venueIds = shows.stream()
			.map(Show::getVenueId)
			.distinct()
			.toList();
		return getVenuesByIds(venueIds).stream()
			.collect(Collectors.toMap(Venue::getId, venue -> venue));
	}

	private List<Venue> getVenuesByIds(List<Long> venueIds) {
		if (venueIds.isEmpty()) {
			return Collections.emptyList();
		}
		return venueRepository.findAllById(venueIds);
	}

	private HomeResponse.ShowSummary toShowSummary(Show show, Map<Long, Venue> venuesById) {
		Venue venue = venuesById.get(show.getVenueId());
		return HomeResponse.ShowSummary.builder()
			.showId(show.getId())
			.posterUrl(toImageUrl(show.getPosterImg()))
			.showTitle(show.getTitle())
			.venueName(venue != null ? venue.getName() : DEFAULT_VENUE_NAME)
			.date(toDateRange(show))
			.build();
	}

	private HomeResponse.ShowList toShowListResponse(
		Page<Show> showPage,
		List<HomeResponse.ShowSummary> summaries
	) {
		return HomeResponse.ShowList.builder()
			.shows(summaries)
			.page(HomeResponse.Page.builder()
				.currentPage(showPage.getNumber() + 1)
				.size(showPage.getSize())
				.totalElements(showPage.getTotalElements())
				.totalPages(showPage.getTotalPages())
				.build())
			.build();
	}

	private boolean isVisibleBanner(HomeBanner banner, Map<Long, Show> showsById, LocalDate today) {
		Show show = showsById.get(banner.getShowId());
		if (show == null) {
			return true;
		}
		return show.getEndTime() == null || !show.getEndTime().toLocalDate().isBefore(today);
	}

	private String toImageUrl(String imageKey) {
		if (!StringUtils.hasText(imageKey)) {
			return null;
		}
		return s3Service.getImageUrl(imageKey);
	}
}
