package com.truve.platform.musical.review.dao;

import com.truve.platform.musical.review.domain.constant.ReviewPointName;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ReviewPointCountDao {
	private ReviewPointName point;
	private Long count;
}
