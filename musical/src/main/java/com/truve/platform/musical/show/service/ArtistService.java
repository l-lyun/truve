package com.truve.platform.musical.show.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.response.Paging;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.musical.s3.S3Service;
import com.truve.platform.musical.show.domain.entity.Artist;
import com.truve.platform.musical.show.domain.entity.ArtistLike;
import com.truve.platform.musical.show.dto.ArtistResponse;
import com.truve.platform.musical.show.repository.ArtistLikeRepository;
import com.truve.platform.musical.show.repository.ArtistMembershipRepository;
import com.truve.platform.musical.show.repository.ArtistNoticeRepository;
import com.truve.platform.musical.show.repository.ArtistRepository;
import com.truve.platform.musical.show.repository.ShowCastingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ArtistService {
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
	private static final String DEFAULT_DATE = "기간 미정";
	private static final String DEFAULT_SHOW_TITLE = "작품명 미정";
	private static final String DEFAULT_VENUE_NAME = "공연장 정보 없음";
	private static final int PAST_SHOW_PREVIEW_SIZE = 15;

	private final ArtistRepository artistRepository;
	private final ArtistLikeRepository artistLikeRepository;
	private final ArtistMembershipRepository artistMembershipRepository;
	private final ArtistNoticeRepository artistNoticeRepository;
	private final ShowCastingRepository showCastingRepository;
	private final S3Service s3Service;

	@Transactional(readOnly = true)
	public ArtistResponse.Detail getDetail(Long artistId, UUID userId) {
		ArtistRepository.ArtistDetailProjection artist = artistRepository.findDetailById(artistId)
			.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_ARTIST));

		return ArtistResponse.Detail.of(
			toArtistResponse(artist, userId),
			toMembershipResponse(artistId, userId),
			getNotices(artistId),
			getCurrentShows(artistId),
			getPastShows(artistId)
		);
	}

	@Transactional(readOnly = true)
	public ArtistResponse.BoardAccess getBoardAccess(Long artistId, UUID userId) {
		Preconditions.validate(artistRepository.existsById(artistId), ErrorCode.NOT_FOUND_ARTIST);

		boolean joined = hasJoinedMembership(artistId, userId);

		return ArtistResponse.BoardAccess.of(joined, joined);
	}

	@Transactional
	public void likeArtist(Long artistId, UUID userId) {
		Preconditions.validate(artistRepository.existsById(artistId), ErrorCode.NOT_FOUND_ARTIST);
		Preconditions.validate(
			!artistLikeRepository.existsByUserIdAndArtistId(userId, artistId),
			ErrorCode.ALREADY_LIKED_ARTIST
		);

		Artist artist = artistRepository.getReferenceById(artistId);
		ArtistLike artistLike = ArtistLike.builder()
			.userId(userId)
			.artist(artist)
			.build();
		try {
			artistLikeRepository.save(artistLike);
		} catch (DataIntegrityViolationException e) {
			throw new CustomException(ErrorCode.ALREADY_LIKED_ARTIST);
		}
	}

	@Transactional
	public void unlikeArtist(Long artistId, UUID userId) {
		artistLikeRepository.deleteByUserIdAndArtistId(userId, artistId);
	}

	@Transactional(readOnly = true)
	public Page<ArtistResponse.ShowSummary> getPastShows(
		Long artistId,
		Paging paging
	) {
		Preconditions.validate(artistRepository.existsById(artistId), ErrorCode.NOT_FOUND_ARTIST);

		Page<ShowCastingRepository.ArtistShowSummaryProjection> showPage = showCastingRepository.findPastShowsByArtistId(
			artistId,
			LocalDateTime.now(),
			paging.toPageable()
		);

		return showPage.map(this::toShowSummary);
	}

	private ArtistResponse.Artist toArtistResponse(
		ArtistRepository.ArtistDetailProjection artist,
		UUID userId
	) {
		return ArtistResponse.Artist.of(
			artist.getArtistId(),
			artist.getArtistName(),
			toImageUrl(artist.getProfileImg()),
			isLikedArtist(artist.getArtistId(), userId)
		);
	}

	private boolean isLikedArtist(Long artistId, UUID userId) {
		return userId != null && artistLikeRepository.existsByUserIdAndArtistId(userId, artistId);
	}

	private ArtistResponse.Membership toMembershipResponse(Long artistId, UUID userId) {
		boolean joined = hasJoinedMembership(artistId, userId);
		return ArtistResponse.Membership.of(joined);
	}

	private boolean hasJoinedMembership(Long artistId, UUID userId) {
		// TODO: 개발 연동 완료 후 실제 멤버십 가입 여부 검증으로 복구
		// return userId != null && artistMembershipRepository.findByUserIdAndArtistId(userId, artistId)
		// 	.map(ArtistMembership::hasActiveEntitlement)
		// 	.orElse(false);
		return true;
	}

	private List<ArtistResponse.Notice> getNotices(Long artistId) {
		return artistNoticeRepository.findNoticesByArtistId(artistId).stream()
			.map(notice -> ArtistResponse.Notice.of(notice.getNoticeId(), notice.getContent()))
			.toList();
	}

	private List<ArtistResponse.ShowSummary> getCurrentShows(Long artistId) {
		return showCastingRepository.findCurrentShowsByArtistId(artistId, LocalDateTime.now()).stream()
			.map(this::toShowSummary)
			.toList();
	}

	private ArtistResponse.PastShowSection getPastShows(Long artistId) {
		List<ArtistResponse.ShowSummary> shows = showCastingRepository.findPastShowsByArtistId(
			artistId,
			LocalDateTime.now(),
			PageRequest.of(0, PAST_SHOW_PREVIEW_SIZE + 1)
		).getContent().stream()
			.map(this::toShowSummary)
			.toList();

		boolean hasMore = shows.size() > PAST_SHOW_PREVIEW_SIZE;
		List<ArtistResponse.ShowSummary> previewShows = hasMore
			? shows.subList(0, PAST_SHOW_PREVIEW_SIZE)
			: shows;

		return ArtistResponse.PastShowSection.of(previewShows, hasMore);
	}

	private ArtistResponse.ShowSummary toShowSummary(ShowCastingRepository.ArtistShowSummaryProjection show) {
		return ArtistResponse.ShowSummary.of(
			show.getShowId(),
			withDefaultShowTitle(show.getShowTitle()),
			toImageUrl(show.getPosterImg()),
			withDefaultVenueName(show.getVenueName()),
			show.getStartTime(),
			show.getEndTime(),
			toDateRange(show.getStartTime(), show.getEndTime())
		);
	}

	private String toImageUrl(String imageKey) {
		if (!StringUtils.hasText(imageKey)) {
			return null;
		}
		return s3Service.getImageUrl(imageKey);
	}

	private String withDefaultShowTitle(String showTitle) {
		return StringUtils.hasText(showTitle) ? showTitle : DEFAULT_SHOW_TITLE;
	}

	private String withDefaultVenueName(String venueName) {
		return StringUtils.hasText(venueName) ? venueName : DEFAULT_VENUE_NAME;
	}

	private String toDateRange(LocalDateTime startTime, LocalDateTime endTime) {
		if (startTime == null || endTime == null) {
			return DEFAULT_DATE;
		}
		return DATE_FORMATTER.format(startTime) + " - " + DATE_FORMATTER.format(endTime);
	}
}
