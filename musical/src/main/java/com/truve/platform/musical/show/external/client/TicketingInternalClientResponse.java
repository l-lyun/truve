package com.truve.platform.musical.show.external.client;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingInternalClientResponse {

	@Getter
	@AllArgsConstructor
	public static class RemainingSeats {
		private Long showScheduleId;
		private List<GradeRemaining> grades;
	}

	@Getter
	@AllArgsConstructor
	public static class GradeRemaining {
		private String gradeName;
		private Long remainingSeatCount;
		private Long totalCount;
	}
}
