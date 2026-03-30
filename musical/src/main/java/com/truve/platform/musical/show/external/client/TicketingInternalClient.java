package com.truve.platform.musical.show.external.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.truve.platform.common.response.ApiResult;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketingInternalClient {

	private final RestClient restClient;

	@Value("${ticketing.server-uri}")
	private String ticketingServerUri;

	public List<TicketingInternalClientResponse.GradeRemaining> getRemainingSeats(Long showScheduleId) {
		ApiResult<TicketingInternalClientResponse.RemainingSeats> response = restClient.get()
			.uri(ticketingServerUri + "/api/ticketing/internal/{showScheduleId}/remaining", showScheduleId)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {
			});

		if (response == null || response.getData() == null || response.getData().getGrades() == null) {
			return List.of();
		}

		return response.getData().getGrades();
	}
}
