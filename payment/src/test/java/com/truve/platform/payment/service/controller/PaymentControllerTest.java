package com.truve.platform.payment.service.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.dto.PaymentResponse;
import com.truve.platform.payment.service.service.PaymentService;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = PaymentController.class)
public class PaymentControllerTest {
	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private PaymentService paymentService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("결제 정보 조회에 성공하면 200 OK와 결제 정보를 응답한다.")
	void 결제_정보_조회() throws Exception {
		// given
		String orderId = "test-order-id";
		PaymentResponse.Details response = PaymentResponse.Details.builder()
			.orderId(orderId)
			.paymentKey("test-payment-key")
			.amount(10000L)
			.method(PaymentMethod.CARD.getDisplayName())
			.status(PaymentStatus.DONE)
			.build();

		given(paymentService.details(orderId)).willReturn(response);

		// when
		ResultActions resultActions = mockMvc.perform(get("/api/payments/{orderId}", orderId));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orderId").value(orderId))
			.andExpect(jsonPath("$.data.paymentKey").value("test-payment-key"))
			.andExpect(jsonPath("$.data.amount").value(10000L))
			.andExpect(jsonPath("$.data.method").value(PaymentMethod.CARD.getDisplayName()))
			.andExpect(jsonPath("$.data.status").value("DONE"));
		verify(paymentService).details(orderId);
	}
}
