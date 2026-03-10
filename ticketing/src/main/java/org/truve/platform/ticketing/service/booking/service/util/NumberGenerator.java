package org.truve.platform.ticketing.service.booking.service.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class NumberGenerator {
	public String generateReservationNumber() {
		String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		return "R" + datePrefix + randomSuffix;
	}

	public String generateTicketNumber() {
		return "T-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
	}
}
