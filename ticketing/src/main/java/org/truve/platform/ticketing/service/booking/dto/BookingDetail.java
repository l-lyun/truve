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

public class BookingDetail {
	@Getter
	@AllArgsConstructor
	@Builder
	public static class Show {
		private final String posterUrl;
		private final String title;
		private final String showDate;
		private final String showTime;
		private final String venueName;

		public static Show from(ShowInfo showInfo) {
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
	public static class Status {
		private final String label;
		private final String subText;

		public static Status from(Reservation reservation) {
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
	public static class Price {
		private final List<Long> ticketUnitPrices;
		private final Long serviceFee;
		private final Long totalPrice;
		private final List<GradePrice> gradePrices;

		public static Price from(Reservation reservation) {
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
	public static class Payment {
		private final String paymentMethod;
		private final String paidAt;
		private final VirtualAccount virtualAccount;

		public static Payment from(Reservation reservation) {
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
		public static class VirtualAccount {
			private final String accountNumber;
			private final String bank;
			private final String customerName;
			private final String dueDate;

			public static VirtualAccount from(Reservation reservation) {
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
	public static class Cancel {
		private final List<String> seats;
		private final Long cancelFee;
		private final Long refundAmount;
		private final String canceledAt;
		private final String method;

		public static Cancel from(Reservation reservation) {
			if (!reservation.isCanceled())
				return null;

			return Cancel.builder()
				.seats(reservation.getCanceledSeatDetails())
				.cancelFee(reservation.getCancelFee())
				.refundAmount(reservation.getRefundAmount())
				.canceledAt(DateTimeUtil.formatDate(reservation.getCanceledAt(), "yyyy.MM.dd(E) HH:mm:ss"))
				.method(reservation.getPaymentMethod())
				.build();
		}
	}

	@Getter
	@AllArgsConstructor
	@Builder
	public static class RefundInfo {
		private final String method;
		private final Long paidAmount;
		private final Long cancelFee;
		private final Long refundAmount;

		public static RefundInfo from(Reservation reservation, List<Long> ticketIds, LocalDateTime canceledAt) {
			return RefundInfo.builder()
				.method(reservation.getPaymentMethod())
				.paidAmount(reservation.getTicketTotalAmount(ticketIds))
				.cancelFee(reservation.calculateCancelFee(canceledAt, ticketIds))
				.refundAmount(reservation.calculateRefundAmount(canceledAt, ticketIds))
				.build();
		}
	}
}
