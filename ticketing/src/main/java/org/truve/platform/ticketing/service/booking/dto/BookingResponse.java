package org.truve.platform.ticketing.service.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.ShowInfo;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
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
		private final Detail.Show show;
		private final Detail.Price price;
		private final List<GradeSeat> gradeSeats;

		public static Order from(Reservation reservation) {
			return Order.builder()
				.reservationNumber(reservation.getNumber())
				.show(Detail.Show.from(reservation.getShowInfo()))
				.price(Detail.Price.from(reservation))
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
		private final Detail.Status status;
		private final Detail.Show show;
		private final String gradeSummary;
		private final Long showId;
		private final boolean canCancel;
		private final boolean canReview;
		private final boolean needsDeposit;

		public static Summary from(Reservation reservation) {
			return Summary.builder()
				.reservationNumber(reservation.getNumber())
				.reservationDate(DateTimeUtil.formatDate(reservation.getBookedAt(), "yyyy.MM.dd"))
				.status(Detail.Status.from(reservation))
				.show(Detail.Show.from(reservation.getShowInfo()))
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
		private final Detail.Status status;
		private final Detail.Show show;
		private final String gradeSummary;
		private final Detail.Price price;
		private final Detail.Payment payment;
		private final Detail.Cancel cancel;

		public static ReservationDetail from(Reservation reservation) {
			return ReservationDetail.builder()
				.reservationNumber(reservation.getNumber())
				.status(Detail.Status.from(reservation))
				.show(Detail.Show.from(reservation.getShowInfo()))
				.gradeSummary(reservation.getGradeSummary())
				.price(Detail.Price.from(reservation))
				.payment(Detail.Payment.from(reservation))
				.cancel(Detail.Cancel.from(reservation))
				.build();
		}
	}

	private static class Detail {

		@Getter
		@AllArgsConstructor
		@Builder
		private static class Show {
			private final String posterUrl;
			private final String title;
			private final String showDate;
			private final String showTime;
			private final String venueName;

			private static Show from(ShowInfo showInfo) {
				return Show.builder()
					.posterUrl(showInfo.getPosterImg())
					.title(showInfo.getTitle())
					.showDate(DateTimeUtil.formatDate(showInfo.getStartAt(), "yyyy.MM.dd(E)"))
					.showTime(DateTimeUtil.formatDate(showInfo.getStartAt(), "a h:mm"))
					.venueName(showInfo.getVenueName())
					.build();
			}
		}

		@Getter
		@AllArgsConstructor
		@Builder
		private static class Status {
			private final String label;
			private final String subText;

			private static Status from(Reservation reservation) {
				return Status.builder()
					.label(reservation.getStatus().getDescription())
					.subText(getSubText(reservation))
					.build();
			}

			private static String getSubText(Reservation reservation) {
				LocalDateTime deadline = reservation.getDeadline();
				if (deadline == null)
					return null;

				if (reservation.isWaitingDeposit())
					return DateTimeUtil.formatDuration(LocalDateTime.now(), deadline, "입금 마감 ", "전");
				else
					return DateTimeUtil.formatDuration(LocalDateTime.now(), deadline, "입장까지 ", "남음");
			}
		}

		@Getter
		@AllArgsConstructor
		@Builder
		private static class Price {
			private final List<Long> ticketUnitPrices;
			private final Long serviceFee;
			private final Long totalPrice;
			private final List<GradePrice> gradePrices;

			private static Price from(Reservation reservation) {
				return Price.builder()
					.ticketUnitPrices(reservation.getTicketPrices())
					.serviceFee(reservation.getServiceFee())
					.totalPrice(reservation.getTotalAmount())
					.gradePrices(getGradePrices(reservation))
					.build();
			}

			private static List<GradePrice> getGradePrices(Reservation reservation) {
				return reservation.getTicketsGroupedByGrade()
					.entrySet().stream()
					.map(entry -> GradePrice.from(entry.getKey(), entry.getValue()))
					.toList();
			}

			@Getter
			@AllArgsConstructor
			@Builder
			private static class GradePrice {
				private final String grade;
				private final int count;
				private final Long totalPrice;

				private static GradePrice from(String grade, List<Ticket> tickets) {
					return GradePrice.builder()
						.grade(grade)
						.count(tickets.size())
						.totalPrice(tickets.stream().mapToLong(Ticket::getPriceSnapshot).sum())
						.build();
				}
			}
		}

		@Getter
		@AllArgsConstructor
		@Builder
		private static class Payment {
			private final String paymentMethod;
			private final String paidAt;
			private final VirtualAccount virtualAccount;

			private static Payment from(Reservation reservation) {
				return Payment.builder()
					.paymentMethod(reservation.getPaymentMethod())
					.paidAt(reservation.getPaidAt() == null ? null :
						DateTimeUtil.formatDate(reservation.getPaidAt(), "yyyy.MM.dd(E) HH:mm:ss"))
					.virtualAccount(VirtualAccount.from(reservation))
					.build();
			}

			@Getter
			@AllArgsConstructor
			@Builder
			private static class VirtualAccount {
				private final String accountNumber;
				private final String bank;
				private final String customerName;
				private final String dueDate;

				private static VirtualAccount from(Reservation reservation) {
					if (reservation.getVirtualAccount() == null)
						return null;

					org.truve.platform.ticketing.service.booking.domain.entity.VirtualAccount virtualAccount = reservation.getVirtualAccount();

					return VirtualAccount.builder()
						.accountNumber(virtualAccount.getAccountNumber())
						.bank(virtualAccount.getBank())
						.customerName(virtualAccount.getCustomerName())
						.dueDate(DateTimeUtil.formatDate(virtualAccount.getDueDate(), "yyyy.MM.dd(E) HH:mm까지"))
						.build();
				}
			}
		}

		@Getter
		@AllArgsConstructor
		@Builder
		private static class Cancel {
			private final List<String> seats;
			private final Long refundFee;
			private final Long refundAmount;
			private final String canceledAt;
			private final String method;

			private static Cancel from(Reservation reservation) {
				if (!reservation.isCanceled())
					return null;

				return Cancel.builder()
					.seats(reservation.getCanceledSeatDetails())
					.refundFee(reservation.getRefundFee())
					.refundAmount(reservation.getRefundAmount())
					.canceledAt(DateTimeUtil.formatDate(reservation.getCanceledAt(), "yyyy.MM.dd(E) HH:mm:ss"))
					.method(reservation.getPaymentMethod())
					.build();
			}
		}
	}
}