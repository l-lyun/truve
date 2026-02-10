package com.truve.platform.payment.service.domain.entity;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.payment.service.domain.constant.CancelType;
import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.domain.constant.PaymentStatus;

class PaymentTest {
	private static final String DEFAULT_ORDER_ID = "ORDER-123";
	private static final Long DEFAULT_AMOUNT = 10000L;
	private static final PaymentMethod DEFAULT_PAYMENT_METHOD = PaymentMethod.CARD;
	private static final String DEFAULT_PAYMENT_KEY = "테스트 결제 키";
	private static final String DEFAULT_CANCEL_REASON = "테스트 결제 취소 사유";

	private Payment createDefaultPayment() {
		return new Payment(DEFAULT_ORDER_ID, DEFAULT_AMOUNT, DEFAULT_PAYMENT_METHOD);
	}

	private Payment createPaymentWithStatus(PaymentStatus status) {
		Payment payment = createDefaultPayment();
		ReflectionTestUtils.setField(payment, "status", status);
		return payment;
	}

	private Payment createWaitPayment() {
		Payment payment = createDefaultPayment();
		payment.waitDeposit(DEFAULT_PAYMENT_KEY);
		return payment;
	}

	private Payment createCompletePayment() {
		Payment payment = createDefaultPayment();
		payment.complete(DEFAULT_PAYMENT_KEY, LocalDateTime.now());
		return payment;
	}

	@Test
	@DisplayName("결제 생성")
	void 결제_생성() {
		// given & when
		Payment payment = new Payment(DEFAULT_ORDER_ID, DEFAULT_AMOUNT, DEFAULT_PAYMENT_METHOD);

		// then
		assertAll(
			() -> assertThat(payment.getCancelableAmount()).isEqualTo(DEFAULT_AMOUNT),
			() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY)
		);
	}

	@Nested
	@DisplayName("입금대기 전환 테스트")
	class WaitDepositTest {

		@Test
		@DisplayName("준비 상태에서 가상계좌가 발급되면 결제 키를 저장하고 입금대기 상태로 전환한다.")
		void 입금대기_전환_성공() {
			// given
			Payment payment = createDefaultPayment();

			// when
			payment.waitDeposit(DEFAULT_PAYMENT_KEY);

			// then
			assertAll(
				() -> assertThat(payment.getPaymentKey()).isEqualTo(DEFAULT_PAYMENT_KEY),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.WAITING_FOR_DEPOSIT)
			);
		}

		@ParameterizedTest(name = "{0} 상태일 때는 입금대기 상태로 전환할 수 없다.")
		@EnumSource(
			value = PaymentStatus.class,
			names = {"READY"},
			mode = EnumSource.Mode.EXCLUDE
		)
		void 입금대기_전환_실패_유효하지_않은_상태(PaymentStatus status) {
			// given
			Payment payment = createPaymentWithStatus(status);

			// when & then
			assertThatThrownBy(() -> payment.waitDeposit(DEFAULT_PAYMENT_KEY))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_STATUS);
		}
	}

	@Nested
	@DisplayName("만료 전환 테스트")
	class ExpireTest {

		@Test
		@DisplayName("입금대기 상태에서 입금 시간이 만료되면 만료 상태로 변경되고 취소 가능 금액을 0으로 변경한다.")
		void 만료_전환_성공() {
			// given
			Payment payment = createWaitPayment();

			// when
			payment.expire();

			// then
			assertAll(
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED),
				() -> assertThat(payment.getCancelableAmount()).isEqualTo(0)
			);
		}

		@ParameterizedTest(name = "{0} 상태일 때는 만료 상태로 전환할 수 없다.")
		@EnumSource(
			value = PaymentStatus.class,
			names = {"WAITING_FOR_DEPOSIT"},
			mode = EnumSource.Mode.EXCLUDE
		)
		void 만료_전환_실패_유효하지_않은_상태(PaymentStatus status) {
			// given
			Payment payment = createPaymentWithStatus(status);

			// when & then
			assertThatThrownBy(payment::expire)
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_STATUS);
		}
	}

	@Nested
	@DisplayName("완료 전환 테스트")
	class CompleteTest {

		@Nested
		@DisplayName("완료 전환 성공 케이스")
		class SuccessCases {

			@Test
			@DisplayName("준비 상태일 때 결제가 완료되면 결제 키를 업데이트하고 완료 상태로 전환한다.")
			void 완료_전환_성공_준비상태() {
				// given
				Payment payment = createDefaultPayment();

				// when
				payment.complete(DEFAULT_PAYMENT_KEY, LocalDateTime.now());

				// then
				assertAll(
					() -> assertThat(payment.getPaymentKey()).isEqualTo(DEFAULT_PAYMENT_KEY),
					() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE)
				);
			}

			@Test
			@DisplayName("입금대기 상태일 때 결제가 완료되면 결제 키를 검증하고 완료 상태로 전환한다.")
			void 완료_전환_성공_입금대기상태() {
				// given
				Payment payment = createDefaultPayment();
				payment.waitDeposit(DEFAULT_PAYMENT_KEY);

				// when
				payment.complete(DEFAULT_PAYMENT_KEY, LocalDateTime.now());

				// then
				assertAll(
					() -> assertThat(payment.getPaymentKey()).isEqualTo(DEFAULT_PAYMENT_KEY),
					() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE)
				);
			}

		}

		@Nested
		@DisplayName("완료 전환 실패 케이스")
		class FailureCases {

			@ParameterizedTest(name = "{0} 상태일 때는 완료 상태로 전환할 수 없다.")
			@EnumSource(
				value = PaymentStatus.class,
				names = {"READY", "WAITING_FOR_DEPOSIT"},
				mode = EnumSource.Mode.EXCLUDE
			)
			void 완료_전환_실패_유효하지_않은_상태(PaymentStatus status) {
				// given
				Payment payment = createPaymentWithStatus(status);

				// when & then
				assertThatThrownBy(() -> payment.complete(DEFAULT_PAYMENT_KEY, LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_STATUS);
			}

			@Test
			@DisplayName("이미 결제 키가 저장되어 있을 경우, 입력받은 결제 키와 일치하지 않으면 완료 상태로 전환할 수 없다.")
			void 완료_전환_실패_유효하지_않은_결제키() {
				// given
				Payment payment = createDefaultPayment();
				payment.waitDeposit(DEFAULT_PAYMENT_KEY);

				// when & then
				assertThatThrownBy(() -> payment.complete("임의의 결제 키", LocalDateTime.now()))
					.isInstanceOf(CustomException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_KEY);
			}
		}
	}

	@Nested
	@DisplayName("결제 취소 테스트")
	class CancelTest {

		@Nested
		@DisplayName("결제 취소 성공 케이스")
		class SuccessCases {

			@Test
			@DisplayName("입금대기 상태일 경우 취소 가능 금액을 업데이트하고 취소 상태로 전환한다.")
			void 결제취소_성공_입금대기상태() {
				// given
				Payment payment = createDefaultPayment();
				payment.waitDeposit("테스트 결제 키");
				Long cancelAmount = DEFAULT_AMOUNT;
				String reason = "테스트 결제 사유";
				CancelType type = CancelType.FULL;

				// when
				payment.applyCancel(cancelAmount, reason, type);

				// then
				assertAll(
					() -> assertThat(payment.getCancelableAmount()).isEqualTo(0),
					() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED)
				);

				assertThat(payment.getCancels())
					.hasSize(1)
					.extracting("payment", "cancelAmount", "cancelReason", "type")
					.containsExactly(tuple(payment, cancelAmount, reason, type));
			}

			@Test
			@DisplayName("결제 완료 상태에서 전액 환불 처리할 경우 취소 가능 금액을 업데이트하고 환불 상태로 전환한다.")
			void 결제취소_성공_완료상태_전액환불() {
				// given
				Payment payment = createDefaultPayment();
				payment.complete("테스트 결제 키", LocalDateTime.now());
				Long cancelAmount = DEFAULT_AMOUNT;
				String reason = "테스트 결제 사유";
				CancelType type = CancelType.FULL;

				// when
				payment.applyCancel(cancelAmount, reason, type);

				// then
				assertAll(
					() -> assertThat(payment.getCancelableAmount()).isEqualTo(0),
					() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED)
				);

				assertThat(payment.getCancels())
					.hasSize(1)
					.extracting("payment", "cancelAmount", "cancelReason", "type")
					.containsExactly(tuple(payment, cancelAmount, reason, type));
			}

			@Test
			@DisplayName("결제 완료 상태에서 부분 환불 처리할 경우 취소 가능 금액을 업데이트하고 부분 환불 상태로 전환한다.")
			void 결제취소_성공_완료상태_부분환불() {
				// given
				Payment payment = createDefaultPayment();
				payment.complete("테스트 결제 키", LocalDateTime.now());
				Long cancelAmount = 6000L;
				String reason = "테스트 결제 사유";
				CancelType type = CancelType.PARTIAL;

				// when
				payment.applyCancel(cancelAmount, reason, type);

				// then
				assertAll(
					() -> assertThat(payment.getCancelableAmount()).isEqualTo(DEFAULT_AMOUNT - cancelAmount),
					() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_REFUNDED)
				);

				assertThat(payment.getCancels())
					.hasSize(1)
					.extracting("payment", "cancelAmount", "cancelReason", "type")
					.containsExactly(tuple(payment, cancelAmount, reason, type));
			}

			@Test
			@DisplayName("결제 완료 상태에서 부분 환불을 여러 번 실행했을 때의 데이터 정합성 및 취소 이력 업데이트 테스트")
			void 결제취소_성공_완료상태_부분환불_여러_번() {
				// given
				Payment payment = createDefaultPayment();
				payment.complete("테스트 결제 키", LocalDateTime.now());
				Long firstCancelAmount = 6000L;
				Long secondCancelAmount = 3000L;
				Long thirdCancelAmount = 1000L;
				String firstReason = "테스트 결제 사유1";
				String secondReason = "테스트 결제 사유2";
				String thirdReason = "테스트 결제 사유3";
				CancelType type = CancelType.PARTIAL;

				// when
				payment.applyCancel(firstCancelAmount, firstReason, type);
				payment.applyCancel(secondCancelAmount, secondReason, type);
				payment.applyCancel(thirdCancelAmount, thirdReason, type);

				// then
				assertAll(
					() -> assertThat(payment.getCancelableAmount()).isEqualTo(0),
					() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED)
				);

				assertThat(payment.getCancels())
					.hasSize(3)
					.extracting("payment", "cancelAmount", "cancelReason", "type")
					.containsExactly(
						tuple(payment, firstCancelAmount, firstReason, type),
						tuple(payment, secondCancelAmount, secondReason, type),
						tuple(payment, thirdCancelAmount, thirdReason, type)
					);
			}
		}

		@Nested
		@DisplayName("결제 취소 실패 케이스")
		class FailureCases {

			@ParameterizedTest(name = "{0} 상태일 때는 결제를 취소할 수 없다.")
			@EnumSource(
				value = PaymentStatus.class,
				names = {"READY", "WAITING_FOR_DEPOSIT", "DONE", "PARTIAL_REFUNDED"},
				mode = EnumSource.Mode.EXCLUDE
			)
			void 결제취소_실패_유효하지_않은_상태(PaymentStatus status) {
				// given
				Payment payment = createPaymentWithStatus(status);

				// when & then
				assertThatThrownBy(() -> payment.applyCancel(DEFAULT_AMOUNT, DEFAULT_CANCEL_REASON, CancelType.FULL))
					.isInstanceOf(CustomException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_STATUS);
			}

			@Test
			@DisplayName("입금대기 상태일 때는 부분 환불 처리할 수 없다.")
			void 결제취소_실패_입금대기_부분환불() {
				// given
				Payment payment = createWaitPayment();

				// when & then
				assertThatThrownBy(() -> payment.applyCancel(6000L, DEFAULT_CANCEL_REASON, CancelType.PARTIAL))
					.isInstanceOf(CustomException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.MUST_CANCEL_FULL);
			}

			@Test
			@DisplayName("취소 금액이 0보다 작으면 결제를 취소할 수 없다.")
			void 결제취소_실패_유효하지_않은_취소금액() {
				// given
				Payment payment = createCompletePayment();
				Long cancelAmount = -1000L;

				// when & then
				assertThatThrownBy(() -> payment.applyCancel(cancelAmount, DEFAULT_CANCEL_REASON, CancelType.PARTIAL))
					.isInstanceOf(CustomException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CANCEL_AMOUNT);
			}

			@Test
			@DisplayName("취소 금액이 취소 가능 금액보다 크면 결제를 취소할 수 없다.")
			void 결제취소_실패_충분하지_않은_취소가능금액() {
				// given
				Payment payment = createCompletePayment();
				Long cancelAmount = 100000000L;

				// when & then
				assertThatThrownBy(() -> payment.applyCancel(cancelAmount, DEFAULT_CANCEL_REASON, CancelType.PARTIAL))
					.isInstanceOf(CustomException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_ENOUGH_CANCELABLE_AMOUNT);
			}

			@Test
			@DisplayName("전액 환불일 때는 결제 총액과 취소 가능 금액과 취소 금액이 일치해야 한다.")
			void 결제취소_실패_전액환불_금액_불일치() {
				// given
				Payment payment = createCompletePayment();

				// when & then
				assertThatThrownBy(() -> payment.applyCancel(5000L, DEFAULT_CANCEL_REASON, CancelType.FULL))
					.isInstanceOf(CustomException.class)
					.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_CANCEL_AMOUNT);
			}
		}
	}

	@Nested
	@DisplayName("결제 금액 검증 테스트")
	class ValidateAmountTest {

		@Test
		@DisplayName("기존 금액과 요청 금액이 다르면 INVALID_PAYMENT_AMOUNT 예외를 던진다")
		void 결제금액_검증_예외() {
			// given
			Payment payment = createDefaultPayment();

			// when & then
			assertThatThrownBy(() -> payment.validateAmount(5000L))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PAYMENT_AMOUNT);
		}

		@Test
		@DisplayName("기존 금액과 요청 금액이 일치하면 예외를 던지지 않는다")
		void it_does_not_throw_when_amount_matches() {
			// given
			Payment payment = createDefaultPayment();

			// when & then
			assertDoesNotThrow(() -> payment.validateAmount(DEFAULT_AMOUNT));
		}
	}
}