package org.truve.platform.ticketing.service.ticketing.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class AdmissionTokenClaimsDTO {
	private UUID userId;
	private Long showId;
	private String tokenType;

	public static AdmissionTokenClaimsDTO of(UUID userId, Long showId, String tokenType) {
		return new  AdmissionTokenClaimsDTO(userId, showId, tokenType);
	}
}
