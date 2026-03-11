package com.truve.platform.common.response;

import java.util.List;

import org.springframework.data.domain.Page;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PageResponse<T> {
	private List<T> content;
	private long totalCount;
	private int totalPages;
	private int page;
	private int size;
	private boolean hasNext;
	private boolean hasPrevious;

	public PageResponse(Page<T> pageData) {
		this.content = pageData.getContent();
		this.totalCount = pageData.getTotalElements();
		this.totalPages = pageData.getTotalPages();
		this.page = pageData.getNumber() + 1;
		this.size = pageData.getSize();
		this.hasNext = pageData.hasNext();
		this.hasPrevious = pageData.hasPrevious();
	}
}