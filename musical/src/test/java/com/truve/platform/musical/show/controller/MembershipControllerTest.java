package com.truve.platform.musical.show.controller;

import static org.mockito.ArgumentMatchers.any;
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
	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final Long ARTIST_ID = 101L;
	private static final String ARTIST_NAME = "고은성";
	private static final long MONTHLY_AMOUNT = 5_000L;

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
		MembershipRequest.CreatePayment request = createPaymentRequest(MembershipPaymentMethod.TOSS_PAY);
		MembershipResponse.CreatePayment response = new MembershipResponse.CreatePayment(
			ARTIST_ID,
			ARTIST_NAME,
			"월간 멤버십",
			MONTHLY_AMOUNT,
			"M20260326ABC123",
			"토스 결제"
		);

		given(membershipService.createPayment(eq(ARTIST_ID), eq(USER_ID), any())).willReturn(response);

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", ARTIST_ID)
				.header("X-User-Id", USER_ID)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.artistId").value(ARTIST_ID))
			.andExpect(jsonPath("$.data.artistName").value(ARTIST_NAME))
			.andExpect(jsonPath("$.data.planName").value("월간 멤버십"))
			.andExpect(jsonPath("$.data.amount").value(MONTHLY_AMOUNT))
			.andExpect(jsonPath("$.data.orderId").value("M20260326ABC123"))
			.andExpect(jsonPath("$.data.paymentMethod").value("토스 결제"));
	}

	@Test
	@DisplayName("존재하지 않는 아티스트의 멤버십 결제 준비 요청은 404를 응답한다.")
	void 존재하지않는_아티스트_멤버십_결제준비_실패() throws Exception {
		MembershipRequest.CreatePayment request = createPaymentRequest(MembershipPaymentMethod.TOSS_PAY);

		willThrow(new CustomException(ErrorCode.NOT_FOUND_ARTIST))
			.given(membershipService)
			.createPayment(eq(999L), eq(USER_ID), any());

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", 999L)
				.header("X-User-Id", USER_ID)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M02"));
	}

	@Test
	@DisplayName("이미 가입한 아티스트의 멤버십 결제 준비 요청은 400을 응답한다.")
	void 이미가입한_아티스트_멤버십_결제준비_실패() throws Exception {
		MembershipRequest.CreatePayment request = createPaymentRequest(MembershipPaymentMethod.BANK_TRANSFER);

		willThrow(new CustomException(ErrorCode.ALREADY_JOINED_ARTIST_MEMBERSHIP))
			.given(membershipService)
			.createPayment(eq(ARTIST_ID), eq(USER_ID), any());

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", ARTIST_ID)
				.header("X-User-Id", USER_ID)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("M04"));
	}

	@Test
	@DisplayName("필수 동의가 누락된 멤버십 결제 준비 요청은 400을 응답한다.")
	void 필수동의_누락_멤버십_결제준비_실패() throws Exception {
		MembershipRequest.CreatePayment request = new MembershipRequest.CreatePayment(
			MembershipPaymentMethod.TOSS_PAY,
			false,
			true,
			true
		);

		mockMvc.perform(post("/api/musical/artists/{artistId}/membership/payment", ARTIST_ID)
				.header("X-User-Id", USER_ID)
				.contentType("application/json")
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value("C02"));
	}

	@Test
	@DisplayName("내 멤버십 조회에 성공하면 요약과 목록을 함께 응답한다.")
	void 내_멤버십_조회_성공() throws Exception {
		MembershipResponse.MyMembership response = MembershipResponse.MyMembership.of(
			MembershipResponse.MyMembershipSummary.of(2, MONTHLY_AMOUNT),
			java.util.List.of(
				MembershipResponse.MyMembershipItem.of(
					3L,
					1L,
					"이재환",
					"https://cdn/leejaehwan.png",
					"ACTIVE",
					"멤버십 가입중",
					"2026.04.03.",
					"2026.05.03.",
					23L,
					5_000L,
					true
				)
			)
		);

		given(membershipService.getMyMembership(USER_ID)).willReturn(response);

		mockMvc.perform(get("/api/musical/my/membership")
				.header("X-User-Id", USER_ID))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.summary.activeMembershipCount").value(2))
			.andExpect(jsonPath("$.data.summary.monthlyPaymentAmount").value(5000))
			.andExpect(jsonPath("$.data.memberships[0].membershipId").value(3L))
			.andExpect(jsonPath("$.data.memberships[0].artistId").value(1L))
			.andExpect(jsonPath("$.data.memberships[0].artistName").value("이재환"))
			.andExpect(jsonPath("$.data.memberships[0].status").value("ACTIVE"))
			.andExpect(jsonPath("$.data.memberships[0].statusLabel").value("멤버십 가입중"));
	}

	private MembershipRequest.CreatePayment createPaymentRequest(MembershipPaymentMethod paymentMethod) {
		return new MembershipRequest.CreatePayment(paymentMethod, true, true, true);
	}
}
