package org.truve.platform.ticketing.service.booking.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateTimeUtil {

	private DateTimeUtil() {
	}

	public static String formatDate(LocalDateTime dateTime, String pattern) {
		DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.KOREAN);
		return dateTime.format(formatter);
	}

	public static String formatDuration(LocalDateTime now, LocalDateTime target, String prefix, String suffix) {
		Duration duration = Duration.between(now, target);
		long days = duration.toDays();
		long hours = duration.toHoursPart();
		long minutes = duration.toMinutesPart();

		StringBuilder sb = new StringBuilder(prefix);
		if (days > 0)
			sb.append(days).append("일 ");
		if (hours > 0)
			sb.append(hours).append("시간 ");
		if (minutes > 0)
			sb.append(minutes).append("분 ");

		if (sb.length() > prefix.length()) {
			sb.setLength(sb.length() - 1);
			sb.append(" ");
		}
		sb.append(suffix);

		return sb.toString();
	}
}
