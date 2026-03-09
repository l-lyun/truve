package org.truve.platform.ticketing.service.ticketing.dto;

import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SeatSectionsDto {
	Long sectionId;
	String sectionName;
	String grade;
	Long price;
	Long seatId;
	String row;
	Long col;
	SeatStatus status;
}
