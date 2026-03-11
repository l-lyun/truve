package org.truve.platform.ticketing.service.booking.domain.entity;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Applicant {
	private String name;
	private String birthDate;
	private String email;
	private String phone;

	@Builder
	public Applicant(String name, String birthDate, String email, String phone) {
		this.name = name;
		this.birthDate = birthDate;
		this.email = email;
		this.phone = phone;
	}
}
