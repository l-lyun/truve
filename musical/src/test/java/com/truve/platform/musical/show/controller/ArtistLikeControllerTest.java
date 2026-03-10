package com.truve.platform.musical.show.controller;

import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.truve.platform.common.exception.ApiAdvice;
import com.truve.platform.musical.MusicalApplication;
import com.truve.platform.musical.show.controller.ArtistLikeController;
import com.truve.platform.musical.show.service.ArtistService;

@WebMvcTest(controllers = ArtistLikeController.class)
@org.springframework.context.annotation.Import(ApiAdvice.class)
@ContextConfiguration(classes = MusicalApplication.class)
class ArtistLikeControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@MockitoBean
	private ArtistService artistService;
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	@DisplayName("배우 좋아요 등록에 성공하면 200 OK를 응답한다.")
	void 배우_좋아요_등록_성공() throws Exception {
		willDoNothing().given(artistService).likeArtist(101L, 7L);

		mockMvc.perform(post("/api/artists/{artistId}/likes", 101L)
				.header("X-User-Id", "7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}

	@Test
	@DisplayName("배우 좋아요 취소에 성공하면 200 OK를 응답한다.")
	void 배우_좋아요_취소_성공() throws Exception {
		willDoNothing().given(artistService).unlikeArtist(101L, 7L);

		mockMvc.perform(delete("/api/artists/{artistId}/likes", 101L)
				.header("X-User-Id", "7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("ok"));
	}
}
