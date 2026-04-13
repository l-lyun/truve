package com.truve.platform.musical.show.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public class ArtistResponse {
	@Getter
	@AllArgsConstructor
	@Builder
	public static class Detail {
		private Artist artist;
		private Membership membership;
		private List<Notice> notices;
		private List<ShowSummary> currentShows;
		private PastShowSection pastShows;

		public static Detail of(
			Artist artist,
			Membership membership,
			List<Notice> notices,
			List<ShowSummary> currentShows,
			PastShowSection pastShows
		) {
			return Detail.builder()
				.artist(artist)
				.membership(membership)
				.notices(notices)
				.currentShows(currentShows)
				.pastShows(pastShows)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Artist {
		private Long artistId;
		private String artistName;
		private String profileImageUrl;
		private Boolean isLiked;

		public static Artist of(Long artistId, String artistName, String profileImageUrl, Boolean isLiked) {
			return Artist.builder()
				.artistId(artistId)
				.artistName(artistName)
				.profileImageUrl(profileImageUrl)
				.isLiked(isLiked)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Membership {
		private Boolean joined;

		public static Membership of(Boolean joined) {
			return Membership.builder()
				.joined(joined)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class BoardAccess {
		private Boolean joined;
		private Boolean accessible;

		public static BoardAccess of(Boolean joined, Boolean accessible) {
			return BoardAccess.builder()
				.joined(joined)
				.accessible(accessible)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Notice {
		private Long noticeId;
		private String content;

		public static Notice of(Long noticeId, String content) {
			return Notice.builder()
				.noticeId(noticeId)
				.content(content)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class ShowSummary {
		private Long showId;
		private String showTitle;
		private String posterUrl;
		private String venueName;
		private LocalDateTime startTime;
		private LocalDateTime endTime;
		private String date;

		public static ShowSummary of(
			Long showId,
			String showTitle,
			String posterUrl,
			String venueName,
			LocalDateTime startTime,
			LocalDateTime endTime,
			String date
		) {
			return ShowSummary.builder()
				.showId(showId)
				.showTitle(showTitle)
				.posterUrl(posterUrl)
				.venueName(venueName)
				.startTime(startTime)
				.endTime(endTime)
				.date(date)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class PastShowSection {
		private List<ShowSummary> shows;
		private Boolean hasMore;

		public static PastShowSection of(List<ShowSummary> shows, Boolean hasMore) {
			return PastShowSection.builder()
				.shows(shows)
				.hasMore(hasMore)
				.build();
		}
	}
}
