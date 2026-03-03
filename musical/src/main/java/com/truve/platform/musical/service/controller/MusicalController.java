package com.truve.platform.musical.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.musical.service.dto.MusicalResponse;
import com.truve.platform.musical.service.service.MusicalService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/musicals")
public class MusicalController {

	private final MusicalService musicalService;

	@Operation(summary = "뮤지컬 상세 조회", description = "뮤지컬 상세 정보를 조회합니다.")
	@GetMapping("/{musicalId}")
	public ApiResult<MusicalResponse.Detail> getDetail(@PathVariable Long musicalId) {
		return ApiResult.ok(musicalService.getDetail(musicalId));
	}
}
