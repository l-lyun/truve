package com.truve.platform.payment.service.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.payment.service.domain.constant.PaymentStatus;
import com.truve.platform.payment.service.domain.entity.EasyPay;
import com.truve.platform.payment.service.domain.entity.Payment;
import com.truve.platform.payment.service.domain.entity.PaymentCancel;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.repository.PaymentCancelRepository;
import com.truve.platform.payment.service.repository.PaymentRepository;
import com.truve.platform.payment.service.service.external.TossClient;
import com.truve.platform.payment.service.service.external.dto.TossResponse;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private PaymentCancelRepository paymentCancelRepository;
	@Mock
	private TossClient tossClient;

	@InjectMocks
	private PaymentService paymentService;

	String orderId;
	Long amount;

	@BeforeEach
	void setUp() {
		orderId = "test-order-id";
		amount = 1000L;
	}

	@Nested
	@DisplayName("결제 생성 테스트")
	class CreateTest {

		PaymentRequest.Create request;

		@BeforeEach
		void setRequest() {
			request = new PaymentRequest.Create(orderId, amount);
		}

		@Test
		@DisplayName("기존 결제가 없으면 새로운 결제를 생성하고 ID를 반환한다.")
		void 결제생성_성공() {
			// given
			Long id = 1L;

			given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

			Payment savedPayment = new Payment(orderId, amount);
			ReflectionTestUtils.setField(savedPayment, "id", id);
			given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

			// when
			Long paymentId = paymentService.create(request);

			// then
			assertThat(paymentId).isEqualTo(id);
			verify(paymentRepository, times(1)).save(any(Payment.class));
		}

		@Test
		@DisplayName("기존 결제가 READY 상태로 존재하면 생성하지 않고 기존 ID를 반환한다")
		void 결제생성_READY상태_존재() {
			// given
			Long id = 1L;

			Payment existingPayment = new Payment(orderId, amount);
			ReflectionTestUtils.setField(existingPayment, "id", id);
			given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(existingPayment));

			// when
			Long paymentId = paymentService.create(request);

			// then
			assertThat(paymentId).isEqualTo(id);
			verify(paymentRepository, never()).save(any());
		}
	}

	@Nested
	@DisplayName("결제 승인 테스트")
	class ConfirmTest {

		@Test
		@DisplayName("토스 승인 후 결제 정보를 업데이트한다.")
		void 결제승인() {
			// given
			Payment payment = Payment.builder().orderId(orderId).amount(amount).build();
			given(paymentRepository.getByOrderIdWithLock(orderId)).willReturn(payment);

			TossResponse.Payment tossResponse = mock(TossResponse.Payment.class);
			given(tossResponse.getMethodDetailsEntity()).willReturn(
				EasyPay.builder().provider("토스").discountAmount(0L).build());
			given(tossResponse.getRequestedAt()).willReturn("2026-03-04T18:00:00+09:00");
			given(tossResponse.getApprovedAt()).willReturn("2026-03-04T18:05:00+09:00");
			given(tossClient.confirm(any())).willReturn(tossResponse);

			// when
			paymentService.confirm(orderId, "paymentKey", amount);

			// then
			assertThat(payment.getStatus()).isNotEqualTo(PaymentStatus.READY);
			verify(tossClient).confirm(any());
		}

	}

	@Nested
	@DisplayName("결제 취소(cancel) 테스트")
	class CancelTest {
		private final String idempotencyKey = "test-idempotency-key";
		private final String transactionKey = "T12345";

		PaymentRequest.Cancel request;

		@BeforeEach
		void setRequest() {
			request = new PaymentRequest.Cancel("사유", amount, null);
		}

		@Test
		@DisplayName("신규 취소 요청 시 토스 API를 호출하고 취소 내역을 저장한다.")
		void 결제취소_신규() {
			// given
			Payment payment = Payment.builder().orderId(orderId).amount(amount).build();
			ReflectionTestUtils.setField(payment, "status", PaymentStatus.DONE);
			given(paymentRepository.getByOrderIdWithLock(orderId)).willReturn(payment);

			TossResponse.Cancel tossResponse = mock(TossResponse.Cancel.class);
			given(tossResponse.getTransactionKey()).willReturn(transactionKey);
			given(tossResponse.getCancelAmount()).willReturn(amount);
			given(tossResponse.getCanceledAt()).willReturn("2026-03-04T22:00:00+09:00");
			given(tossResponse.getCancelStatus()).willReturn("DONE");
			given(tossClient.cancel(any(), any(), any())).willReturn(tossResponse);

			given(paymentCancelRepository.findByTransactionKey(transactionKey)).willReturn(Optional.empty());

			// when
			paymentService.cancel(orderId, idempotencyKey, request);

			// then
			assertAll(
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED),
				() -> assertThat(payment.getCancelableAmount()).isEqualTo(0L),
				() -> verify(tossClient).cancel(any(), eq(idempotencyKey), any()),
				() -> assertThat(payment.getCancels()).hasSize(1)
			);
		}

		@Test
		@DisplayName("이미 취소된 건(transactionKey 존재)이면 토스 API만 호출하고 중복 저장하지 않는다.")
		void 결제취소_중복요청_멱등성() {
			// given
			Payment payment = Payment.builder().orderId(orderId).amount(amount).build();
			given(paymentRepository.getByOrderIdWithLock(orderId)).willReturn(payment);

			TossResponse.Cancel tossResponse = mock(TossResponse.Cancel.class);
			given(tossResponse.getTransactionKey()).willReturn(transactionKey);
			given(tossClient.cancel(any(), any(), any())).willReturn(tossResponse);

			PaymentCancel existingCancel = mock(PaymentCancel.class);
			given(existingCancel.getCancelStatus()).willReturn("DONE");
			given(paymentCancelRepository.findByTransactionKey(transactionKey))
				.willReturn(Optional.of(existingCancel));

			// when
			paymentService.cancel(orderId, idempotencyKey, request);

			// then
			assertAll(
				() -> assertThat(payment.getCancels()).isEmpty(),
				() -> verify(tossClient).cancel(any(), eq(idempotencyKey), any())
			);
		}
	}
}