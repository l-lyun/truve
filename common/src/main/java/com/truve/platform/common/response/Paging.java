package com.truve.platform.common.response;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Paging {
	@Min(1)
	@Max(100000)
	private int page = 1;

	@Min(1)
	@Max(100)
	private int size = 10;

	public Pageable toPageable() {
		return PageRequest.of(page - 1, size);
	}
}
