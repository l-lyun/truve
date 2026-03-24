package com.truve.platform.auth.service.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.truve.platform.auth.service.domain.dto.response.AuthResponse;
import com.truve.platform.auth.service.security.AuthCookieManager;
import com.truve.platform.auth.service.security.config.SecurityConfig;
import com.truve.platform.auth.service.service.AuthService;

import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(controllers = AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private AuthService authService;
	@MockitoBean
	private AuthCookieManager authCookieManager;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("내 정보 조회에 성공하면 200 OK와 사용자 정보를 반환한다.")
	void 내정보조회_성공() throws Exception {
		// given
		given(authService.getMe("access-token"))
			.willReturn(new AuthResponse.Me("user@test.com", "tester", false));

		// when
		ResultActions resultActions = mockMvc.perform(get("/api/auth/me")
			.header("X-Token", "access-token"));

		// then
		resultActions.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"))
			.andExpect(jsonPath("$.data.email").value("user@test.com"))
			.andExpect(jsonPath("$.data.nickname").value("tester"))
			.andExpect(jsonPath("$.data.marketingInfoAgreed").value(false));
		verify(authService).getMe("access-token");
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
		verify(authCookieManager).clearRefreshToken(org.mockito.ArgumentMatchers.any(HttpServletResponse.class));
	}
}
