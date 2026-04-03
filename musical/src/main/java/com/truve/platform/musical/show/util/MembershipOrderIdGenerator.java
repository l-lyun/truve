package com.truve.platform.musical.show.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class MembershipOrderIdGenerator {

	private MembershipOrderIdGenerator() {
	}

	public static String generate() {
		String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String randomSuffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
		return "M" + datePrefix + randomSuffix;
	}
}