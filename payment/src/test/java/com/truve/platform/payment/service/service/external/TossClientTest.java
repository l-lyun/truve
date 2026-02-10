package com.truve.platform.payment.service.service.external;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.truve.platform.payment.service.service.external.dto.TossRequest;
import com.truve.platform.payment.service.service.external.dto.TossResponse;

@RestClientTest(TossClient.class)
@TestPropertySource(properties = {
	"toss.payment.base-url=https://api.tosspayments.com/v1/payments/",
	"toss.payment.secret-key=test_sk_123456789"
})
class TossClientTest {

	@Autowired
	private TossClient tossClient;

	@Autowired
	private MockRestServiceServer mockServer;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@TestConfiguration
	static class TestConfig {
		@Bean
		public RestClient restClient(RestClient.Builder builder) {
			return builder.build();
		}
	}

	@Test
	@DisplayName("토스 결제 승인 API를 호출하여 결과 결제 정보를 응답받는다.")
	void 결제승인_호출_성공() {
		// given
		TossRequest.Confirm request = new TossRequest.Confirm("orderId", 100L, "paymentKey");
		String expectedHeader =
			"Basic " + Base64.getEncoder().encodeToString(("test_sk_123456789" + ":").getBytes(StandardCharsets.UTF_8));
		String expectedResponse = """
			{
					"paymentKey": "paymentKey",
			    "status": "DONE",
			    "totalAmount": 100
			}
			""";

		mockServer.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
			.andExpect(header("Authorization", expectedHeader))
			.andRespond(withSuccess(expectedResponse, MediaType.APPLICATION_JSON));

		// when
		TossResponse.Payment response = tossClient.confirm(request);

		// then
		assertAll(
			() -> assertThat(response.getPaymentKey()).isEqualTo("paymentKey"),
			() -> assertThat(response.getStatus()).isEqualTo("DONE"),
			() -> assertThat(response.getTotalAmount()).isEqualTo(100L)
		);
		mockServer.verify();
	}

}