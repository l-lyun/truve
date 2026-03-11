package com.truve.platform.payment.service.external.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TossClient {

	private final RestClient restClient;

	@Value("${toss.payment.base-url}")
	private String baseUrl;

	@Value("${toss.payment.secret-key}")
	private String secretKey;

	public TossResponse.Payment confirm(TossRequest.Confirm request) {
		try {
			return restClient.post()
				.uri(URI.create(baseUrl + "confirm"))
				.header("Authorization", getAuthorizations())
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(TossResponse.Payment.class);
		} catch (RestClientResponseException e) {
			throw handleTossError(e);
		}
	}

	private String getAuthorizations() {
		return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
	}

	public TossResponse.Cancel cancel(String paymentKey, String idempotencyKey, TossRequest.Cancel request) {
		try {
			TossResponse.Payment response = restClient.post()
				.uri(URI.create(baseUrl + paymentKey + "/cancel"))
				.header("Authorization", getAuthorizations())
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(TossResponse.Payment.class);
			return response != null ? response.getCancels().getLast() : null;
		} catch (RestClientResponseException e) {
			throw handleTossError(e);
		}
	}

	private CustomException handleTossError(RestClientResponseException e) {
		TossResponse.Error error = e.getResponseBodyAs(TossResponse.Error.class);

		String errorMessage =
			(error != null && error.getMessage() != null) ? error.getMessage() : "결제 대행사 통신 중 오류가 발생했습니다.";

		return new CustomException(ErrorCode.EXTERNAL_PAYMENT_ERROR, errorMessage);
	}
}
