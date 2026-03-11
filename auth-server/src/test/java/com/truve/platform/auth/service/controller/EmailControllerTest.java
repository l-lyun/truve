package com.truve.platform.auth.service.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.truve.platform.auth.service.security.config.SecurityConfig;
import com.truve.platform.auth.service.service.EmailService;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;


@WebMvcTest(controllers = EmailController.class)
@Import(SecurityConfig.class)
class EmailControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private EmailService emailService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("인증 코드 전송에 성공하면 200 OK를 반환한다.")
	void 인증코드_전송_성공() throws Exception {
		// given
		String body = """
			{
			  "email": "new@test.com"
			}
			""";

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/email/send-code")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
		verify(emailService).sendMail("new@test.com");
	}

	@Test
	@DisplayName("중복 이메일로 인증 코드 전송 요청하면 400을 반환한다.")
	void 인증코드_전송_실패_중복_이메일() throws Exception {
		// given
		String body = """
			{
			  "email": "dup@test.com"
			}
			""";
		willThrow(new CustomException(ErrorCode.ALREADY_EXISTS_EMAIL))
			.given(emailService).sendMail(anyString());

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/email/send-code")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value(ErrorCode.ALREADY_EXISTS_EMAIL.getCode()));
	}

	@Test
	@DisplayName("인증 코드 검증에 성공하면 200 OK를 반환한다.")
	void 인증코드_검증_성공() throws Exception {
		// given
		String body = """
			{
			  "email": "new@test.com",
			  "code": "123456"
			}
			""";

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/email/verify")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));

		verify(emailService).verifyEmail("new@test.com", "123456");
	}

	@Test
	@DisplayName("인증 코드가 올바르지 않으면 400을 반환한다.")
	void 인증코드_검증_실패_코드_불일치() throws Exception {
		// given
		String body = """
			{
			  "email": "new@test.com",
			  "code": "wrong"
			}
			""";
		willThrow(new CustomException(ErrorCode.NOT_CORRECT_EMAIL_CODE))
			.given(emailService).verifyEmail(anyString(), anyString());

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/email/verify")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value(ErrorCode.NOT_CORRECT_EMAIL_CODE.getCode()));
	}
}
