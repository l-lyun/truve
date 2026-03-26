package com.truve.platform.musical.review.dto;

import java.util.List;

import com.truve.platform.musical.review.domain.constant.ReviewPointName;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ReviewRequest {

	@AllArgsConstructor
	@Getter
	@NoArgsConstructor
	public static class Create {

		@NotNull
		Boolean isPositive;

		@NotNull
		@Size(min = 1, max = 5)
		List<ReviewPointName> emotionPoints;

		@NotNull
		@Size(min = 1, max = 5)
		List<ReviewPointName> charmPoints;

		@NotBlank
		String content;

		@NotBlank
		String title;

	}
}
