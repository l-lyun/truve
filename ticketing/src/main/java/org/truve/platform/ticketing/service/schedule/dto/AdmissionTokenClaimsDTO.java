package org.truve.platform.ticketing.service.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class AdmissionTokenClaimsDTO {
	private Long userId;
	private Long showId;
	private String tokenType;

	public static AdmissionTokenClaimsDTO of(Long userId, Long showId, String tokenType) {
		return new  AdmissionTokenClaimsDTO(userId, showId, tokenType);
	}
}
