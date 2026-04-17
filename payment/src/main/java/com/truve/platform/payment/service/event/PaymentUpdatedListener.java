package com.truve.platform.payment.service.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.truve.platform.payment.service.external.kafka.booking.BookingEventCommand;
import com.truve.platform.payment.service.external.kafka.booking.BookingPublisher;
import com.truve.platform.payment.service.external.kafka.membership.MembershipEventCommand;
import com.truve.platform.payment.service.external.kafka.membership.MembershipPublisher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentUpdatedListener {
	private static final String MEMBERSHIP_ORDER_PREFIX = "M";

	private final BookingPublisher bookingPublisher;
	private final MembershipPublisher membershipPublisher;

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void onConfirmed(PaymentUpdated.Confirmed event) {
		if (isMembershipOrder(event.getOrderId())) {
			if (event.getStatus() == com.truve.platform.payment.service.domain.constant.PaymentStatus.DONE) {
				membershipPublisher.publish(new MembershipEventCommand.Confirmed(event.getOrderId()));
			}
			return;
		}

		bookingPublisher.publish(
			new BookingEventCommand.Confirmed(
				event.getOrderId(),
				event.getRequestedAt(),
				event.getApprovedAt(),
				event.getMethod().getDisplayName(),
				event.getVirtualAccount() == null ? null
					: new BookingEventCommand.Confirmed.VirtualAccount(
					event.getVirtualAccount().getAccountNumber(),
					event.getVirtualAccount().getBank().getBankName(),
					event.getVirtualAccount().getCustomerName(),
					event.getVirtualAccount().getDueDate()
				)
			));
	}

	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void onDepositReceived(PaymentUpdated.DepositReceived event) {
		if (isMembershipOrder(event.getOrderId())) {
			membershipPublisher.publish(new MembershipEventCommand.DepositReceived(event.getOrderId()));
			return;
		}

		bookingPublisher.publish(
			new BookingEventCommand.DepositReceived(
				event.getOrderId(),
				event.getApprovedAt()
			)
		);
	}

	private boolean isMembershipOrder(String orderId) {
		return orderId != null && orderId.startsWith(MEMBERSHIP_ORDER_PREFIX);
	}
}
