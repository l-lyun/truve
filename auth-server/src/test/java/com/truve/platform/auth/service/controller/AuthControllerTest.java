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
import org.springframework.data.util.Pair;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.truve.platform.auth.service.security.AuthCookieManager;
import com.truve.platform.auth.service.security.config.SecurityConfig;
import com.truve.platform.auth.service.service.AuthService;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private AuthService authService;
	@MockitoBean
	private AuthCookieManager authCookieManager;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("회원가입에 성공하면 200 OK를 반환한다.")
	void 회원가입_성공() throws Exception {
		// given
		String body = """
			{
			  "email": "new@test.com",
			  "password": "password123",
			  "nickname": "tester",
			  "serviceTermsAgreed": true,
			  "electronicFinanceTermsAgreed": true,
			  "privacyCollectionAgreed": true,
			  "marketingInfoAgreed": false,
			  "over14Agreed": true
			}
			""";

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/sign-up")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
		verify(authService).signUp("new@test.com", "tester", "password123", true, true, true, false, true);
	}

	@Test
	@DisplayName("중복 이메일로 회원가입 요청하면 400을 반환한다.")
	void 회원가입_실패_중복_이메일() throws Exception {
		// given
		String body = """
			{
			  "email": "dup@test.com",
			  "password": "password123",
			  "nickname": "tester",
			  "serviceTermsAgreed": true,
			  "electronicFinanceTermsAgreed": true,
			  "privacyCollectionAgreed": true,
			  "marketingInfoAgreed": false,
			  "over14Agreed": true
			}
			""";
		willThrow(new CustomException(ErrorCode.ALREADY_EXISTS_EMAIL))
			.given(authService).signUp(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/sign-up")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value(ErrorCode.ALREADY_EXISTS_EMAIL.getCode()));
	}

	@Test
	@DisplayName("필수 약관 미동의로 회원가입 요청하면 400을 반환한다.")
	void 회원가입_실패_필수_약관_미동의() throws Exception {
		// given
		String body = """
			{
			  "email": "new@test.com",
			  "password": "password123",
			  "nickname": "tester",
			  "serviceTermsAgreed": true,
			  "electronicFinanceTermsAgreed": false,
			  "privacyCollectionAgreed": true,
			  "marketingInfoAgreed": false,
			  "over14Agreed": true
			}
			""";
		willThrow(new CustomException(ErrorCode.REQUIRED_TERMS_NOT_AGREED))
			.given(authService).signUp(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/sign-up")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value(ErrorCode.REQUIRED_TERMS_NOT_AGREED.getCode()));
	}

	@Test
	@DisplayName("닉네임 형식 오류로 회원가입 요청하면 400을 반환한다.")
	void 회원가입_실패_닉네임_형식_오류() throws Exception {
		// given
		String body = """
			{
			  "email": "new@test.com",
			  "password": "password123",
			  "nickname": "a b",
			  "serviceTermsAgreed": true,
			  "electronicFinanceTermsAgreed": true,
			  "privacyCollectionAgreed": true,
			  "marketingInfoAgreed": false,
			  "over14Agreed": true
			}
			""";
		willThrow(new CustomException(ErrorCode.INVALID_NICKNAME))
			.given(authService).signUp(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean());

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/sign-up")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorType").value("CLIENT_ERROR"))
			.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_NICKNAME.getCode()));
	}

	@Test
	@DisplayName("로그인에 성공하면 accessToken을 반환하고 refreshToken 쿠키를 설정한다.")
	void 로그인_성공() throws Exception {
		// given
		String body = """
			{
			  "email": "user@test.com",
			  "password": "password123"
			}
			""";
		given(authService.login("user@test.com", "password123"))
			.willReturn(Pair.of("access-token", "refresh-token"));

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").value("access-token"));
		verify(authCookieManager).setRefreshToken(any(HttpServletResponse.class), eq("refresh-token"), eq(1209600L));
	}

	@Test
	@DisplayName("토큰 재발급에 성공하면 새 accessToken을 반환한다.")
	void 토큰_재발급_성공() throws Exception {
		// given
		given(authService.reissue("old-refresh-token"))
			.willReturn(Pair.of("new-access-token", "new-refresh-token"));

		// when
		ResultActions resultActions = mockMvc.perform(post("/api/auth/reissue")
			.cookie(new jakarta.servlet.http.Cookie("refreshToken", "old-refresh-token")));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.accessToken").value("new-access-token"));
		verify(authCookieManager).setRefreshToken(any(HttpServletResponse.class), eq("new-refresh-token"), eq(1209600L));
	}

	@Test
	@DisplayName("로그아웃에 성공하면 200 OK를 반환하고 refreshToken 쿠키를 제거한다.")
	void 로그아웃_성공() throws Exception {
		// when
		ResultActions resultActions = mockMvc.perform(delete("/api/auth/logout")
			.header("X-Token", "access-token"));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
		verify(authService).logout("access-token");
		verify(authCookieManager).clearRefreshToken(any(HttpServletResponse.class));
	}
}
