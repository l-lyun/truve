package com.truve.platform.musical.board.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class BoardRequest {

	@Getter
	@AllArgsConstructor
	@NoArgsConstructor
	public static class CreateComment {
		@NotBlank
		private String content;
	}
}
