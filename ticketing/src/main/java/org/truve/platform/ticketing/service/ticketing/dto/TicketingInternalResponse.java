package org.truve.platform.ticketing.service.ticketing.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingInternalResponse {

	public interface FlatRemainingSeatInfo {
		String getGradeName();
		Long getRemainingSeatCount();
		Long getTotalCount();
	}

	@Getter
	@AllArgsConstructor
	public static class RemainingSeats {
		private Long showScheduleId;
		private List<GradeRemaining> grades;

		public static RemainingSeats of(Long showScheduleId, List<GradeRemaining> grades) {
			return new RemainingSeats(showScheduleId, grades);
		}
	}

	@Getter
	@AllArgsConstructor
	public static class GradeRemaining {
		private String gradeName;
		private Long remainingSeatCount;
		private Long totalCount;

		public static  GradeRemaining from(FlatRemainingSeatInfo flatRemainingSeatInfo) {
			return new GradeRemaining(
				flatRemainingSeatInfo.getGradeName(),
				flatRemainingSeatInfo.getRemainingSeatCount(),
				flatRemainingSeatInfo.getTotalCount()
			);
		}
	}
}
