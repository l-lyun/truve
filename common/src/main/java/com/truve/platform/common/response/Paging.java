package com.truve.platform.common.response;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Paging {
	@Min(1)
	int page;
	@Min(1)
	int size;

	public Pageable toPageable() {
		return PageRequest.of(page - 1, size);
	}
}
