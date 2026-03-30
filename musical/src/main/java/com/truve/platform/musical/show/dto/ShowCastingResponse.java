package com.truve.platform.musical.show.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class ShowCastingResponse {

	@Getter
	@AllArgsConstructor
	@Builder
	@Schema(name = "ShowCastingScheduleDetailResponse")
	public static class Detail {
		private Long showId;
		private Range range;
		private Filters filters;
		private List<Role> roles;
		private Page page;
		private List<Row> rows;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Range {
		private LocalDate from;
		private LocalDate to;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Filters {
		private List<FilterArtist> artists;
	}

    // 공연 전체 배우 목록
	@Getter
	@AllArgsConstructor
	@Builder
	public static class FilterArtist {
		private Long artistId;
		private String artistName;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Role {
		private String roleName;
		private Integer order;
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
	public static class Row {
		private Long scheduleId;
		private LocalDateTime showTime;
		private String showDateLabel;
		private String showTimeLabel;
		private Map<String, CastArtist> casts;
		private List<GradeRemaining> remainingSeats;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class GradeRemaining {
		private String gradeName;
		private Long remainingSeatCount;
		private Long totalCount;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class CastArtist {
		private Long artistId;
		private String artistName;
	}
}