package org.truve.platform.ticketing.service.booking.risk.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.booking.risk.domain.entity.BookingBotRiskSummary;
import org.truve.platform.ticketing.service.booking.risk.dto.BeBotRiskReportRequest;
import org.truve.platform.ticketing.service.booking.risk.repository.BookingBotRiskSummaryRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class BookingBotRiskServiceTest {

	@Mock
	private BookingBotRiskSummaryRepository bookingBotRiskSummaryRepository;

	@InjectMocks
	private BookingBotRiskService bookingBotRiskService;

	@Test
	@DisplayName("같은 orderId로 들어온 BE bot 판정은 중복 집계하지 않는다.")
	void 같은주문번호는_중복집계하지않는다() {
		// given
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BookingBotRiskSummary summary = BookingBotRiskSummary.create(userId);
		ReflectionTestUtils.setField(summary, "lastOrderId", "order-1");

		BeBotRiskReportRequest request = new BeBotRiskReportRequest();
		ReflectionTestUtils.setField(request, "userId", userId);
		ReflectionTestUtils.setField(request, "orderId", "order-1");
		ReflectionTestUtils.setField(request, "label", "bot");

		given(bookingBotRiskSummaryRepository.findByUserId(userId)).willReturn(Optional.of(summary));

		// when
		bookingBotRiskService.reportBeRisk(request);

		// then
		verify(bookingBotRiskSummaryRepository, never()).save(any());
	}

	@Test
	@DisplayName("차단 기간 안의 유저는 paymentReady 전에 PAYMENT_RESTRICTED_BY_RISK 예외가 발생한다.")
	void 차단유저는_paymentReady전에_예외가발생한다() {
		// given
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		BookingBotRiskSummary summary = BookingBotRiskSummary.create(userId);
		ReflectionTestUtils.setField(summary, "blockedUntil", LocalDateTime.now().plusHours(1));

		given(bookingBotRiskSummaryRepository.findByUserId(userId)).willReturn(Optional.of(summary));

		// when
		CustomException exception = assertThrows(
			CustomException.class,
			() -> bookingBotRiskService.validatePaymentReady(userId)
		);

		// then
		assertAll(
			() -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_RESTRICTED_BY_RISK),
			() -> assertThat(exception.getMessage()).contains("후 다시 시도해 주세요")
		);
	}
}
