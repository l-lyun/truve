package com.truve.platform.payment.service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.external.kafka.BookingEventCommand;
import com.truve.platform.payment.service.external.kafka.BookingPublisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentUpdatedListener {

	private final BookingPublisher bookingPublisher;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onConfirmed(PaymentUpdated.Confirmed event) {
		bookingPublisher.publish(
			new BookingEventCommand.Confirmed(
				event.getOrderId(),
				event.getApprovedAt(),
				event.getStatus() == PaymentStatus.WAITING_FOR_DEPOSIT
			)
		);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onDepositReceived(PaymentUpdated.DepositReceived event) {
		bookingPublisher.publish(
			new BookingEventCommand.DepositReceived(
				event.getOrderId(),
				event.getApprovedAt()
			)
		);
	}
}
