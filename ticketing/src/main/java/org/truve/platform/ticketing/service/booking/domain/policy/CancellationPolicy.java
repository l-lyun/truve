package org.truve.platform.ticketing.service.booking.domain.policy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;

import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

public class CancellationPolicy {

	public static Long calculate(Reservation reservation, LocalDateTime cancelAt) {
		return calculate(reservation, reservation.getTickets(), cancelAt);
	}

	public static Long calculate(Reservation reservation, List<Ticket> tickets, LocalDateTime cancelAt) {
		LocalDateTime showAt = reservation.getShowInfo().getStartAt();
		long daysUntilShow = ChronoUnit.DAYS.between(cancelAt.toLocalDate(), showAt.toLocalDate());
		long daysSinceBooked = ChronoUnit.DAYS.between(reservation.getBookedAt().toLocalDate(), cancelAt.toLocalDate());
		boolean isBookedDay = cancelAt.toLocalDate().equals(reservation.getBookedAt().toLocalDate());

		Preconditions.validate(daysUntilShow > 0, ErrorCode.CANCEL_NOT_ALLOWED);

		if (isFreeCancelPeriod(isBookedDay, daysUntilShow, daysSinceBooked))
			return 0L;

		if (daysUntilShow <= 9)
			return calculatePercentFee(tickets, daysUntilShow);

		return calculateFlatFee(tickets);
	}

	private static boolean isFreeCancelPeriod(boolean isBookedDay, long daysUntilShow, long daysSinceBooked) {
		return isBookedDay || (daysSinceBooked <= 7 && daysUntilShow > 9);
	}

	private static Long calculatePercentFee(List<Ticket> tickets, long daysUntilShow) {
		if (daysUntilShow <= 1)
			return applyPercent(tickets, 30);
		if (daysUntilShow <= 3)
			return applyPercent(tickets, 20);
		return applyPercent(tickets, 10);
	}

	private static Long calculateFlatFee(List<Ticket> tickets) {
		long flatFee = (long)tickets.size() * 4000L;
		long maxFee = applyPercent(tickets, 10);
		return Math.min(flatFee, maxFee);
	}

	private static Long applyPercent(List<Ticket> tickets, int percent) {
		return tickets.stream()
			.mapToLong(t -> t.getPriceSnapshot() * percent / 100)
			.sum();
	}
}
