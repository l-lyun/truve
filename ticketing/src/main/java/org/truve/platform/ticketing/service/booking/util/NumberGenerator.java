package org.truve.platform.ticketing.service.booking.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class NumberGenerator {

	private NumberGenerator() {
	}

	public static String generateReservationNumber() {
		String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		return "R" + datePrefix + randomSuffix;
	}

	public static String generateTicketNumber() {
		return "T-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
	}

	public static String generateIdempotencyKey(String reservationNumber, List<Long> ticketIds) {
		String sorted = ticketIds.stream()
			.sorted()
			.map(String::valueOf)
			.collect(Collectors.joining(","));
		return UUID.nameUUIDFromBytes((reservationNumber + ":" + sorted).getBytes()).toString();
	}
}
