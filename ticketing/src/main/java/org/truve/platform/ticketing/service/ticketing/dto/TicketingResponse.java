package org.truve.platform.ticketing.service.ticketing.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class TicketingResponse {

	@Getter
	@AllArgsConstructor
	public static class Enter {
		String sessionToken;
		long expireIn;
	}

	@Getter
	@AllArgsConstructor
	public static class ShowSeats {

	}

	@Getter
	@AllArgsConstructor
	// TODO: 공연 일정 추가 필요
	public static class Show {
		String title;
		String venueName;
		LocalDateTime startAt;
		public static TicketingResponse.Show of(String title,  String venueName, LocalDateTime startAt) {
			return new TicketingResponse.Show(title, venueName, startAt);
		}
	}

	@Getter
	@AllArgsConstructor
	public static class Seats {
		List<Section> sections;

		public static Seats from (List<SeatSectionsDto> sectionsDto) {
			Map<Long, SectionAccumulator> sectionMap = new LinkedHashMap<>();

			for (SeatSectionsDto seatSectionDto : sectionsDto) {
				SectionAccumulator section = sectionMap.computeIfAbsent(

					seatSectionDto.getSectionId(),
					sectionId -> new SectionAccumulator(
						sectionId,
						seatSectionDto.getSectionName(),
						seatSectionDto.getGrade(),
						seatSectionDto.getPrice()
					)
				);
				section.addSeat(seatSectionDto);
			}

			List<Section> sections = sectionMap.values()
				.stream()
				.map(SectionAccumulator::toSection)
				.toList();

			return new Seats(sections);
		}
	}

	@Getter
	@AllArgsConstructor
	public static class Section {
		Long sectionId;
		String sectionName;
		String grade;
		Long price;
		List<Row> rows;
	}

	@Getter
	@AllArgsConstructor
	public static class Row {
		String row;
		List<Seat> seats;
	}

	@Getter
	@AllArgsConstructor
	public static class Seat {
		Long scheduledSeatId;
		Long col;
		SeatStatus status;
	}



	private static class SectionAccumulator {
		private final Long sectionId;
		private final String sectionName;
		private final String grade;
		private final Long price;
		private final Map<String, RowAccumulator> rowMap = new LinkedHashMap<>();

		private SectionAccumulator(Long sectionId, String sectionName, String grade, Long price) {
			this.sectionId = sectionId;
			this.sectionName = sectionName;
			this.grade = grade;
			this.price = price;
		}

		private void addSeat(SeatSectionsDto seatSectionDto) {
			RowAccumulator row = rowMap.computeIfAbsent(
				seatSectionDto.getRow(),
				RowAccumulator::new
			);
			row.addSeat(seatSectionDto);
		}

		private Section toSection() {
			List<Row> rows = rowMap.values()
				.stream()
				.map(RowAccumulator::toRow)
				.toList();

			return new Section(sectionId, sectionName, grade, price, rows);
		}
	}

	private static class RowAccumulator {
		private final String row;
		private final List<Seat> seats = new ArrayList<>();

		private RowAccumulator(String row) {
			this.row = row;
		}

		private void addSeat(SeatSectionsDto seatSectionDto) {
			seats.add(new Seat(
				seatSectionDto.getScheduledSeatId(),
				seatSectionDto.getCol(),
				seatSectionDto.getStatus()
			));
		}
		
		private Row toRow() {
			return new Row(row, seats);
		}
	}
}
