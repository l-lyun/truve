package com.truve.platform.payment.service.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.service.PaymentService;

import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = PaymentController.class)
public class PaymentControllerTest {
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@MockitoBean
	private PaymentService paymentService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("결제 생성에 성공하면 200 OK와 생성된 paymentId를 응답한다.")
	void 결제_생성() throws Exception {
		// given
		var request = new PaymentRequest.Create("orderId", 100L, PaymentMethod.CARD);
		given(paymentService.create(any())).willReturn(1L);

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/payments")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)));

		// then
		resultActions.andExpect(status().isOk());
		verify(paymentService).create(any(PaymentRequest.Create.class));
	}

	@Test
	@DisplayName("중복된 orderId로 요청한 경우, 400과 ALREADY_EXIST_PAYMENT를 응답한다. ")
	void 결제_생성_중복_에러_테스트() throws Exception {
		// given
		var request = new PaymentRequest.Create("orderId", 100L, PaymentMethod.CARD);
		given(paymentService.create(any()))
			.willThrow(new CustomException(ErrorCode.ALREADY_EXIST_PAYMENT));

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/payments")
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(request)));

		// then
		resultActions.andExpect(status().isBadRequest()) // 400 에러가 나는지 확인
			.andExpect(jsonPath("$.code").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.message").exists());
	}
}
