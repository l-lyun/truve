package com.truve.platform.show.service.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class ShowResponse {

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Detail {
		private Long showId;
		private String title;
		private String description;
		private Integer runtimeMin;
		private Integer ageLimit;
		private String posterUrl;
		private String noticeUrl;
		private LocalDateTime startTime;
		private LocalDateTime endTime;
		private Venue venue;
		private List<Schedule> schedules;
		private List<SeatGrade> seatGrades;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Venue {
		private Long venueId;
		private String name;
		private String address;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Schedule {
		private Long scheduleId;
		private LocalDateTime showTime;
		private String status;
		private List<Casting> castings;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Casting {
		private Long showCastId;
		private Long artistId;
		private String artistName;
		private String roleName;
		private Integer order;
		private Boolean isLiked;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class SeatGrade {
		private Long showSeatGradeId;
		private String gradeName;
		private Integer basePrice;
		private String colorCode;
	}
}
