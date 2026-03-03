package com.truve.platform.musical.service.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class MusicalResponse {

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Detail {
		private Long musicalId;
		private String title;
		private String posterUrl;
		private String stage;
		private String runningTime;
		private String ageLimit;
		private String priceInfo;
		private LocalDate startDate;
		private LocalDate endDate;
		private LocalDateTime openAt;
		private Double ratingAverage;
		private Integer weeklyRank;
		private Integer reviewCount;
		private String timeInfo;
		private String noticeUrl;
		private String detailsUrl;
		private List<Schedule> schedules;
		private List<SeatPrice> seatPrices;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Schedule {
		private Long scheduleId;
		private LocalDateTime dateTime;
		private Boolean isAvailable;
		private List<Actor> actors;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Actor {
		private Long actorId;
		private String role;
		private String name;
		private Boolean isLiked;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class SeatPrice {
		private String seatGrade;
		private Integer price;
	}
}
