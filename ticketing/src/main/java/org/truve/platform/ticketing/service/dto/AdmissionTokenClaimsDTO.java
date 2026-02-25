package org.truve.platform.ticketing.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class AdmissionTokenClaimsDTO {
	private String userId;
	private String showId;
	private String tokenType;

	public static AdmissionTokenClaimsDTO of(String userId, String showId, String tokenType) {
		return new  AdmissionTokenClaimsDTO(userId, showId, tokenType);
	}
}
