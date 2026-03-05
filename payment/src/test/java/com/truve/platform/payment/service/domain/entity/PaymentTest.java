package com.truve.platform.payment.service.domain.entity;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.payment.service.domain.command.CancelCommand;
import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.domain.constant.PaymentStatus;

class PaymentTest {
	private static final Long DEFAULT_AMOUNT = 1000L;

	Payment payment;
	LocalDateTime now;

	@BeforeEach
	void setup() {
		payment = Payment.builder()
			.orderId("ORDER-123")
			.amount(DEFAULT_AMOUNT)
			.build();
		now = LocalDateTime.now();
	}

	@Nested
	@DisplayName("결제 승인 테스트")
	class ConfirmTest {

		@Test
		@DisplayName("카드 결제를 승인하면 카드 정보를 저장하고 결제 상태가 DONE이 된다.")
		void 결제승인_카드() {
			// given
			String paymentKey = "Test Payment Key";
			Card card = Card.builder()
				.issuerCode("3K")
				.number("1234")
				.installmentPlanMonths(0)
				.build();

			// when
			payment.confirm(paymentKey, card, now, now);

			// then
			assertAll(
				() -> assertThat(payment.getPaymentKey()).isEqualTo(paymentKey),
				() -> assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD),
				() -> assertThat(payment.getCard()).isEqualTo(card),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE)
			);
		}

		@Test
		@DisplayName("간편결제를 승인하면 간편결제 정보를 저장하고 결제 상태가 DONE이 된다.")
		void 결제승인_간편() {
			// given
			String paymentKey = "Test Payment Key";
			EasyPay easyPay = EasyPay.builder()
				.provider("토스페이")
				.discountAmount(0L)
				.build();

			// when
			payment.confirm(paymentKey, easyPay, now, now);

			// then
			assertAll(
				() -> assertThat(payment.getPaymentKey()).isEqualTo(paymentKey),
				() -> assertThat(payment.getMethod()).isEqualTo(PaymentMethod.EASY_PAY),
				() -> assertThat(payment.getEasyPay()).isEqualTo(easyPay),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE)
			);
		}

		@Test
		@DisplayName("가상계좌를 승인하면 가상계좌 정보를 저장하고 결제 상태가 WAITING_FOR_DEPOSIT이 된다.")
		void 결제승인_가상계좌() {
			// given
			String paymentKey = "Test Payment Key";
			VirtualAccount account = VirtualAccount.builder()
				.accountNumber("number")
				.bankCode("06")
				.customerName("테스트")
				.dueDate(now)
				.build();

			// when
			payment.confirm(paymentKey, account, now, now);

			// then
			assertAll(
				() -> assertThat(payment.getPaymentKey()).isEqualTo(paymentKey),
				() -> assertThat(payment.getMethod()).isEqualTo(PaymentMethod.VIRTUAL_ACCOUNT),
				() -> assertThat(payment.getVirtualAccount()).isEqualTo(account),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_FOR_DEPOSIT)
			);
		}

		@Test
		@DisplayName("이미 승인된 결제를 다시 승인하려 하면 예외가 발생한다.")
		void 결제승인_중복요청() {
			// given
			String paymentKey = "Test Payment Key";
			Card card = Card.builder()
				.issuerCode("3K")
				.number("1234")
				.installmentPlanMonths(0)
				.build();

			payment.confirm(paymentKey, card, now, now);

			// when & then
			assertThatThrownBy(() -> payment.confirm("Test Payment Key 2", card, now, now))
				.isInstanceOf(CustomException.class);
		}

		@Test
		@DisplayName("지원하지 않는 결제 수단의 정보가 들어오면 예외를 반환한다.")
		void 결제승인_지원하지_않는_결제수단() {
			// given
			String paymentKey = "Test Payment Key";
			String methodDetails = "이상한 결제 수단 정보";

			// when & then
			assertThatThrownBy(() -> payment.confirm(paymentKey, methodDetails, now, now))
				.isInstanceOf(CustomException.class);
		}
	}

	@Nested
	@DisplayName("결제 취소 테스트")
	class CancelTest {
		private CancelCommand.CancelCommandBuilder commandBuilder;

		@BeforeEach
		void setUp() {
			commandBuilder = CancelCommand.builder()
				.fee(0L)
				.reason("테스트 취소 사유")
				.canceledAt(now)
				.transactionKey("테스트 트랜잭션 키")
				.status("취소 완료");
		}

		@Test
		@DisplayName("입금 대기(WAITING_FOR_DEPOSIT) 상태에서 전액 취소하면 상태가 CANCELED가 된다.")
		void 결제취소_입금대기_전액취소() {
			// given
			setupPaymentWithStatus(PaymentStatus.WAITING_FOR_DEPOSIT);
			CancelCommand command = commandBuilder.amount(DEFAULT_AMOUNT).build();

			// when
			payment.validateCancel(command.getAmount());
			payment.applyCancel(command);

			// then
			assertAll(
				() -> assertThat(payment.getCancelableAmount()).isEqualTo(0L),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED),
				() -> assertThat(payment.getCancels()).hasSize(1)
			);
		}

		@Test
		@DisplayName("입금 대기 상태에서 부분 취소를 시도하면 예외가 발생한다.")
		void 결제취소_입금대기_부분취소_불가() {
			// given
			setupPaymentWithStatus(PaymentStatus.WAITING_FOR_DEPOSIT);
			Long partialAmount = DEFAULT_AMOUNT - 100L;

			// when & then
			assertThatThrownBy(() -> payment.validateCancel(partialAmount))
				.isInstanceOf(CustomException.class);
		}

		@Test
		@DisplayName("결제 완료(DONE) 상태에서 전액 취소하면 상태가 REFUNDED가 된다.")
		void 결제취소_완료상태_전액취소() {
			// given
			setupPaymentWithStatus(PaymentStatus.DONE);
			CancelCommand command = commandBuilder.amount(DEFAULT_AMOUNT).build();

			// when
			payment.validateCancel(command.getAmount());
			payment.applyCancel(command);

			// then
			assertAll(
				() -> assertThat(payment.getCancelableAmount()).isEqualTo(0L),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED)
			);
		}

		@Test
		@DisplayName("결제 완료 상태에서 일부 금액만 취소하면 상태가 PARTIAL_REFUNDED가 된다.")
		void 결제취소_완료상태_부분취소() {
			// given
			setupPaymentWithStatus(PaymentStatus.DONE);
			Long partialAmount = 400L;
			CancelCommand command = commandBuilder.amount(partialAmount).build();

			// when
			payment.validateCancel(command.getAmount());
			payment.applyCancel(command);

			// then
			assertAll(
				() -> assertThat(payment.getCancelableAmount()).isEqualTo(DEFAULT_AMOUNT - partialAmount),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED)
			);
		}

		@Test
		@DisplayName("남은 취소 가능 금액보다 큰 금액을 취소하려 하면 예외가 발생한다.")
		void 결제취소_금액초과_예외() {
			// given
			setupPaymentWithStatus(PaymentStatus.DONE);
			Long overAmount = DEFAULT_AMOUNT + 1L;

			// when & then
			assertThatThrownBy(() -> payment.validateCancel(overAmount))
				.isInstanceOf(CustomException.class);
		}

		@Test
		@DisplayName("이미 전액 취소된 결제를 다시 취소하려 하면 예외가 발생한다.")
		void 결제취소_이미취소된상태_예외() {
			// given
			setupPaymentWithStatus(PaymentStatus.CANCELED);

			// when & then
			assertThatThrownBy(() -> payment.validateCancel(DEFAULT_AMOUNT))
				.isInstanceOf(CustomException.class);
		}

		private void setupPaymentWithStatus(PaymentStatus targetStatus) {
			VirtualAccount account = VirtualAccount.builder()
				.accountNumber("number")
				.bankCode("06")
				.customerName("테스트")
				.dueDate(now)
				.build();
			Card card = Card.builder()
				.issuerCode("3K")
				.number("1234")
				.installmentPlanMonths(0)
				.build();

			if (targetStatus == PaymentStatus.WAITING_FOR_DEPOSIT) {
				payment.confirm("key", account, now, now);
			} else if (targetStatus == PaymentStatus.DONE) {
				payment.confirm("key", card, now, now);
			} else if (targetStatus == PaymentStatus.CANCELED) {
				payment.confirm("key", account, now, now);
				payment.applyCancel(commandBuilder.amount(DEFAULT_AMOUNT).build());
			}
		}
	}

}