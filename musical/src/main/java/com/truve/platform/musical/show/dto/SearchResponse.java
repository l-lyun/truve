package com.truve.platform.musical.show.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class SearchResponse {

	@Getter
	@AllArgsConstructor
	@Builder
	public static class SearchResult {
		private String keyword;
		private int artistCount;
		private int showCount;
		private boolean hasMoreArtists;
		private boolean hasMoreShows;
		private List<ArtistSummary> artists;
		private List<ShowSummary> shows;

		public static SearchResult of(
			String keyword,
			List<ArtistSummary> artists,
			List<ShowSummary> shows,
			int totalArtistCount,
			int totalShowCount,
			boolean hasMoreArtists,
			boolean hasMoreShows
		) {
			return SearchResult.builder()
				.keyword(keyword)
				.artistCount(totalArtistCount)
				.showCount(totalShowCount)
				.hasMoreArtists(hasMoreArtists)
				.hasMoreShows(hasMoreShows)
				.artists(artists)
				.shows(shows)
				.build();
		}

		public static SearchResult empty(String keyword) {
			return SearchResult.builder()
				.keyword(keyword)
				.artistCount(0)
				.showCount(0)
				.hasMoreArtists(false)
				.hasMoreShows(false)
				.artists(List.of())
				.shows(List.of())
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class ArtistSummary {
		private Long artistId;
		private String artistName;
		private String profileImageUrl;
		private String appearanceInfo;

		public static ArtistSummary of(
			Long artistId,
			String artistName,
			String profileImageUrl,
			String appearanceInfo
		) {
			return ArtistSummary.builder()
				.artistId(artistId)
				.artistName(artistName)
				.profileImageUrl(profileImageUrl)
				.appearanceInfo(appearanceInfo)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class ShowSummary {
		private Long showId;
		private String posterUrl;
		private String title;
		private String venueName;
		private String date;

		public static ShowSummary of(
			Long showId,
			String posterUrl,
			String title,
			String venueName,
			String date
		) {
			return ShowSummary.builder()
				.showId(showId)
				.posterUrl(posterUrl)
				.title(title)
				.venueName(venueName)
				.date(date)
				.build();
		}
	}
}