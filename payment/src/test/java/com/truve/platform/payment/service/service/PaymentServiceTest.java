package com.truve.platform.payment.service.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.payment.service.domain.constant.PaymentMethod;
import com.truve.platform.payment.service.domain.entity.Payment;
import com.truve.platform.payment.service.dto.PaymentRequest;
import com.truve.platform.payment.service.repository.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
	@Mock
	private PaymentRepository paymentRepository;

	@InjectMocks
	private PaymentService paymentService;

	@Nested
	@DisplayName("결제 생성 테스트")
	class createPaymentTest {

		private final PaymentRequest.Create request = new PaymentRequest.Create("orderId", 100L, PaymentMethod.CARD);

		@Test
		@DisplayName("새로운 결제를 요청하면 전달받은 결제 정보를 저장한다.")
		void 결제_생성_성공() {
			// given
			var savedPayment = new Payment(request.getOrderId(), request.getAmount(), request.getMethod());
			ReflectionTestUtils.setField(savedPayment, "id", 1L);

			given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

			// when
			Long paymentId = paymentService.create(request);

			// then
			assertThat(paymentId).isEqualTo(1L);
			verify(paymentRepository).save(any(Payment.class));
		}

		@Test
		@DisplayName("이미 동일한 주문 ID로 결제가 존재하면 예외가 발생한다.")
		void 결제_생성_실패_중복_주문() {
			// given
			given(paymentRepository.existsByOrderId(request.getOrderId())).willReturn(true);

			// when
			CustomException exception = assertThrows(CustomException.class, () -> {
				paymentService.create(request);
			});

			// then
			verify(paymentRepository, never()).save(any(Payment.class));
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXIST_PAYMENT);
		}
	}
}