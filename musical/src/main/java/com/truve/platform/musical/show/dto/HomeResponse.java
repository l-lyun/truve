package com.truve.platform.musical.show.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class HomeResponse {

	@Getter
	@AllArgsConstructor
	@Builder
	public static class ShowList {
		private List<ShowSummary> shows;
		private Page page;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class ShowSummary {
		private Long showId;
		private String posterUrl;
		private String showTitle;
		private String venueName;
		private String date;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Page {
		private int currentPage;
		private int size;
		private long totalElements;
		private int totalPages;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class BannerList {
		private List<Banner> banners;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Banner {
		private Long bannerId;
		private Long showId;
		private String showTitle;
		private String venueName;
		private String date;
		private String posterUrl;
		private Integer displayOrder;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class PromotionShowList {
		private int totalCount;
		private List<PromotionShow> shows;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class PromotionShow {
		private Integer displayOrder;
		private Long showId;
		private String posterUrl;
	}
}
