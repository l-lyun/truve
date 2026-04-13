package com.truve.platform.musical.show.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.MusicalApplication;
import com.truve.platform.musical.show.domain.constant.MembershipPaymentMethod;
import com.truve.platform.musical.show.dto.MembershipRequest;
import com.truve.platform.musical.show.dto.MembershipResponse;
import com.truve.platform.musical.show.service.MembershipService;

@WebMvcTest(controllers = MembershipController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
@ContextConfiguration(classes = MusicalApplication.class)
class MembershipControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MembershipService membershipService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("아티스트 멤버십 결제 준비에 성공하면 200 OK와 주문 정보를 응답한다.")
	void 아티스트_멤버십_결제준비_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.TOSS_PAY,
			true,
			true,
			true
		);
		MembershipResponse.CreatePayment response = new MembershipResponse.CreatePayment(
			101L,
			"고은성",
			"월간 멤버십",
			5_000L,
			"M20260326ABC123",
			"토스 결제"
		);

		given(membershipService.createPayment(eq(101L), eq(userId), any())).willReturn(response);

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", 101L)
				.header("X-User-Id", userId)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.artistId").value(101L))
			.andExpect(jsonPath("$.data.artistName").value("고은성"))
			.andExpect(jsonPath("$.data.planName").value("월간 멤버십"))
			.andExpect(jsonPath("$.data.amount").value(5000L))
			.andExpect(jsonPath("$.data.orderId").value("M20260326ABC123"))
			.andExpect(jsonPath("$.data.paymentMethod").value("토스 결제"));
	}

	@Test
	@DisplayName("존재하지 않는 아티스트의 멤버십 결제 준비 요청은 404를 응답한다.")
	void 존재하지않는_아티스트_멤버십_결제준비_실패() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.TOSS_PAY,
			true,
			true,
			true
		);

		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST))
			.given(membershipService)
			.createPayment(eq(999L), eq(userId), any());

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", 999L)
				.header("X-User-Id", userId)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M02"));
	}

	@Test
	@DisplayName("이미 가입한 아티스트의 멤버십 결제 준비 요청은 400을 응답한다.")
	void 이미가입한_아티스트_멤버십_결제준비_실패() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.BANK_TRANSFER,
			true,
			true,
			true
		);

		willThrow(new CustomException(ErrorCode.ALREADY_JOINED_ARTIST_MEMBERSHIP))
			.given(membershipService)
			.createPayment(eq(101L), eq(userId), any());

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", 101L)
				.header("X-User-Id", userId)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M04"));
	}

	@Test
	@DisplayName("필수 동의가 누락된 멤버십 결제 준비 요청은 400을 응답한다.")
	void 필수동의_누락_멤버십_결제준비_실패() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.TOSS_PAY,
			false,
			true,
			true
		);

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", 101L)
				.header("X-User-Id", userId)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("C02"));
	}

	@Test
	@DisplayName("아티스트 멤버십 가입 완료 정보 조회에 성공하면 200 OK와 완료 정보를 응답한다.")
	void 아티스트_멤버십_가입완료_조회_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		MembershipResponse.Complete response = new MembershipResponse.Complete(
			101L,
			"고은성",
			"월간 멤버십",
			5_000L,
			"2026. 4. 3.",
			"2026. 5. 3."
		);

		given(membershipService.complete(101L, userId)).willReturn(response);

		mockMvc.perform(get("/api/musical/artists/{artistId}/membership/complete", 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.artistId").value(101L))
			.andExpect(jsonPath("$.data.artistName").value("고은성"))
			.andExpect(jsonPath("$.data.planName").value("월간 멤버십"))
			.andExpect(jsonPath("$.data.amount").value(5000L))
			.andExpect(jsonPath("$.data.joinedAt").value("2026. 4. 3."))
			.andExpect(jsonPath("$.data.nextBillingAt").value("2026. 5. 3."));
	}

	@Test
	@DisplayName("결제가 아직 완료되지 않은 멤버십의 완료 정보 조회는 400을 응답한다.")
	void 결제미완료_멤버십_가입완료_조회_실패() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

		willThrow(new CustomException(ErrorCode.MEMBERSHIP_PAYMENT_NOT_COMPLETED))
			.given(membershipService)
			.complete(101L, userId);

		mockMvc.perform(get("/api/musical/artists/{artistId}/membership/complete", 101L)
				.header("X-User-Id", userId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M06"));
	}
}
