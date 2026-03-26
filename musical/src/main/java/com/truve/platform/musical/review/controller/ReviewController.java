package com.truve.platform.musical.review.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.truve.platform.common.response.ApiResult;
import com.truve.platform.common.response.PageResponse;
import com.truve.platform.common.response.Paging;
import com.truve.platform.musical.review.domain.constant.ReviewSortType;
import com.truve.platform.musical.review.dto.ReviewRequest;
import com.truve.platform.musical.review.dto.ReviewResponse;
import com.truve.platform.musical.review.service.ReviewService;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/musical/reviews")
public class ReviewController {

	private static final String USER_ID_HEADER = "X-User-Id";

	private final ReviewService reviewService;

	@PostMapping("/{showId}")
	public ApiResult<Void> create(
		@Parameter(hidden = true)
		@RequestHeader(value = USER_ID_HEADER) UUID userId,
		@PathVariable	Long showId,
		@RequestBody @Valid ReviewRequest.Create request
	) {
		reviewService.create(userId, showId, request);
		return ApiResult.ok();
	}

	@GetMapping("/{showId}")
	public ApiResult<PageResponse<ReviewResponse.ReviewItem>> getReviews(
		@Parameter(hidden = true)
		@PathVariable Long showId,
		@RequestParam(required = false, defaultValue = "LATEST") ReviewSortType sort,
		@Valid Paging paging
	) {
		Page<ReviewResponse.ReviewItem> response = reviewService.getReviews(showId, sort, paging);

		return ApiResult.ok(response);
	}

	@GetMapping("/{showId}/meta")
	public ApiResult<ReviewResponse.Search> getReviewMeta(
		@Parameter(hidden = true)
		@PathVariable Long showId
	) {
		var response = reviewService.getReviewMeta(showId);

		return ApiResult.ok(response);
	}

}
