package org.truve.platform.ticketing.service.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.util.DateTimeUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class BookingResponse {

	@Getter
	@AllArgsConstructor
	public static class Create {
		private final String reservationNumber;
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Order {
		private final String reservationNumber;
		private final BookingDetail.Show show;
		private final BookingDetail.Price price;
		private final List<GradeSeat> gradeSeats;

		public static Order from(Reservation reservation) {
			return Order.builder()
				.reservationNumber(reservation.getNumber())
				.show(BookingDetail.Show.from(reservation.getShowInfo()))
				.price(BookingDetail.Price.from(reservation))
				.gradeSeats(getGradeSeats(reservation))
				.build();
		}

		private static List<GradeSeat> getGradeSeats(Reservation reservation) {
			return reservation.getTicketsGroupedByGrade()
				.entrySet().stream()
				.map(entry -> GradeSeat.from(entry.getKey(), entry.getValue()))
				.toList();
		}

		@Getter
		@AllArgsConstructor
		@Builder
		public static class GradeSeat {
			private final String grade;
			private final Long price;
			private final int count;
			private final List<String> seatDetails;

			public static GradeSeat from(String grade, List<Ticket> tickets) {
				return GradeSeat.builder()
					.grade(grade)
					.price(tickets.getFirst().getPriceSnapshot())
					.count(tickets.size())
					.seatDetails(tickets.stream().map(Ticket::getSeatDetail).toList())
					.build();
			}
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Summary {
		private final String reservationNumber;
		private final String reservationDate;
		private final BookingDetail.Status status;
		private final BookingDetail.Show show;
		private final String gradeSummary;
		private final Long showId;
		private final boolean canCancel;
		private final boolean canReview;
		private final boolean needsDeposit;

		public static Summary from(Reservation reservation) {
			return Summary.builder()
				.reservationNumber(reservation.getNumber())
				.reservationDate(DateTimeUtil.formatDate(reservation.getBookedAt(), "yyyy.MM.dd"))
				.status(BookingDetail.Status.from(reservation))
				.show(BookingDetail.Show.from(reservation.getShowInfo()))
				.gradeSummary(reservation.getGradeSummary())
				.showId(reservation.getShowInfo().getShowId())
				.canCancel(reservation.isCancelable())
				.canReview(reservation.isReviewable())
				.needsDeposit(reservation.isWaitingDeposit())
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class ReservationDetail {
		private final String reservationNumber;
		private final BookingDetail.Status status;
		private final BookingDetail.Show show;
		private final String gradeSummary;
		private final BookingDetail.Price price;
		private final BookingDetail.Payment payment;
		private final BookingDetail.Cancel cancel;

		public static ReservationDetail from(Reservation reservation) {
			return ReservationDetail.builder()
				.reservationNumber(reservation.getNumber())
				.status(BookingDetail.Status.from(reservation))
				.show(BookingDetail.Show.from(reservation.getShowInfo()))
				.gradeSummary(reservation.getGradeSummary())
				.price(BookingDetail.Price.from(reservation))
				.payment(BookingDetail.Payment.from(reservation))
				.cancel(BookingDetail.Cancel.from(reservation))
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class Cancel {
		private final BookingDetail.RefundInfo refundInfo;
		private final List<TicketInfo> tickets;
		private final String status;

		public static Cancel from(Reservation reservation, List<Long> ticketIds, LocalDateTime canceledAt) {
			String title = formatTitle(reservation.getShowInfo());
			return Cancel.builder()
				.refundInfo(BookingDetail.RefundInfo.from(reservation, ticketIds, canceledAt))
				.tickets(getTicketInfos(reservation.getTickets(), title))
				.status("환불 완료")
				.build();
		}

		private static String formatTitle(ShowInfo showInfo) {
			String date = DateTimeUtil.formatDate(showInfo.getStartAt(), "yyyy.MM.dd.(E)");
			return showInfo.getTitle() + " " + date;
		}

		private static List<TicketInfo> getTicketInfos(List<Ticket> tickets, String title) {
			return tickets.stream().map(t -> TicketInfo.from(t, title)).toList();
		}

		@Getter
		@AllArgsConstructor
		@Builder
		private static class TicketInfo {
			private final Long ticketId;
			private final String title;
			private final String seatDetail;

			public static TicketInfo from(Ticket ticket, String title) {
				return TicketInfo.builder()
					.ticketId(ticket.getId())
					.title(title)
					.seatDetail(ticket.getSeatDetail())
					.build();
			}
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class CanceledTickets {
		private final List<Long> canceledTicketIds;
	}
}