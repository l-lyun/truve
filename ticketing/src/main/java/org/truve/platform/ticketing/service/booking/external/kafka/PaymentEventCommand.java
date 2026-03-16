package org.truve.platform.ticketing.service.booking.external.kafka;

import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class PaymentEventCommand {

	@Getter
	@Builder
	@AllArgsConstructor
	public static class Create {
		private String orderId;
		private Long amount;

		public static Create of(Reservation reservation) {
			return Create.builder()
				.orderId(reservation.getNumber())
				.amount(reservation.getTotalAmount() + reservation.getServiceFee())
				.build();
		}
	}
}
