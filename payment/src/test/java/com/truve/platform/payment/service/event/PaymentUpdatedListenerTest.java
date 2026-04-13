package com.truve.platform.payment.service.event;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.external.kafka.BookingPublisher;
import com.truve.platform.payment.service.external.kafka.MembershipEventCommand;
import com.truve.platform.payment.service.external.kafka.MembershipPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentUpdatedListenerTest {

	@Mock
	private BookingPublisher bookingPublisher;
	@Mock
	private MembershipPublisher membershipPublisher;

	@InjectMocks
	private PaymentUpdatedListener paymentUpdatedListener;

	@Test
	@DisplayName("멤버십 결제가 DONE으로 확정되면 membership completion 이벤트를 발행한다.")
	void 멤버십_결제확정_이벤트_발행() {
		PaymentUpdated.Confirmed event = new PaymentUpdated.Confirmed(
			"M20260402123456",
			LocalDateTime.now(),
			LocalDateTime.now(),
			PaymentMethod.EASY_PAY,
			null,
			PaymentStatus.DONE
		);

		paymentUpdatedListener.onConfirmed(event);

		verify(membershipPublisher).publish(argThat(command ->
			command instanceof MembershipEventCommand.Confirmed
				&& command.getOrderId().equals("M20260402123456")
		));
		verify(bookingPublisher, never()).publish(org.mockito.Mockito.any());
	}

	@Test
	@DisplayName("멤버십 가상계좌 결제가 WAITING_FOR_DEPOSIT면 confirm 단계에서 completion 이벤트를 발행하지 않는다.")
	void 멤버십_가상계좌_confirm_미발행() {
		PaymentUpdated.Confirmed event = new PaymentUpdated.Confirmed(
			"M20260402123456",
			LocalDateTime.now(),
			null,
			PaymentMethod.VIRTUAL_ACCOUNT,
			null,
			PaymentStatus.WAITING_FOR_DEPOSIT
		);

		paymentUpdatedListener.onConfirmed(event);

		verify(membershipPublisher, never()).publish(org.mockito.Mockito.any());
		verify(bookingPublisher, never()).publish(org.mockito.Mockito.any());
	}

	@Test
	@DisplayName("멤버십 입금 완료 이벤트가 오면 membership deposit 이벤트를 발행한다.")
	void 멤버십_입금완료_이벤트_발행() {
		PaymentUpdated.DepositReceived event = new PaymentUpdated.DepositReceived(
			"M20260402123456",
			LocalDateTime.now()
		);

		paymentUpdatedListener.onDepositReceived(event);

		verify(membershipPublisher).publish(argThat(command ->
			command instanceof MembershipEventCommand.DepositReceived
				&& command.getOrderId().equals("M20260402123456")
		));
		verify(bookingPublisher, never()).publish(org.mockito.Mockito.any());
	}
}
