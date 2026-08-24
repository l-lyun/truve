package org.truve.platform.ticketing.service.booking.dto;

import java.util.List;

import org.truve.platform.ticketing.service.booking.domain.entity.embedded.Applicant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class BookingRequest {

	@Getter
	@AllArgsConstructor
	public static class Create {
		@NotNull
		private Long showScheduleId;

		@NotNull
		@Size(min = 1, max = 4)
		private List<Long> scheduledSeatIds;
	}

	@Getter
	@AllArgsConstructor
	public static class ApplicantInfo {
		@NotBlank
		private String name;
		@NotBlank
		private String birthDate;
		@NotBlank
		@Email
		private String email;
		@NotBlank
		private String phone;

		public Applicant toEntity() {
			return Applicant.builder()
				.name(this.name)
				.birthDate(this.birthDate)
				.email(this.email)
				.phone(this.phone)
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	public static class Cancel {
		@NotEmpty
		private String cancelReason;
		@NotEmpty
		private List<Long> ticketIds;
	}
}
