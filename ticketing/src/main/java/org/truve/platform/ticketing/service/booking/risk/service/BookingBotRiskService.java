package org.truve.platform.ticketing.service.booking.risk.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.truve.platform.ticketing.service.booking.risk.domain.entity.BookingBotRiskSummary;
import org.truve.platform.ticketing.service.booking.risk.dto.BeBotRiskReportRequest;
import org.truve.platform.ticketing.service.booking.risk.repository.BookingBotRiskSummaryRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingBotRiskService {

	private final BookingBotRiskSummaryRepository bookingBotRiskSummaryRepository;

	@Transactional
	public void reportBeRisk(BeBotRiskReportRequest request) {
		if (!request.isBot() || request.getUserId() == null) {
			return;
		}

		BookingBotRiskSummary summary = bookingBotRiskSummaryRepository.findByUserId(request.getUserId())
			.orElseGet(() -> BookingBotRiskSummary.create(request.getUserId()));

		if (summary.isDuplicateOrder(request.getOrderId())) {
			return;
		}

		summary.applyBotDetection(request, LocalDateTime.now());
		bookingBotRiskSummaryRepository.save(summary);
	}

	@Transactional(readOnly = true)
	public void validatePaymentReady(UUID userId) {
		LocalDateTime now = LocalDateTime.now();

		bookingBotRiskSummaryRepository.findByUserId(userId)
			.filter(summary -> summary.isBlocked(now))
			.ifPresent(summary -> {
				throw new CustomException(
					ErrorCode.PAYMENT_RESTRICTED_BY_RISK,
					buildBlockedMessage(now, summary.getBlockedUntil())
				);
			});
	}

	private String buildBlockedMessage(LocalDateTime now, LocalDateTime blockedUntil) {
		if (blockedUntil == null) {
			return ErrorCode.PAYMENT_RESTRICTED_BY_RISK.getMessage();
		}

		Duration duration = Duration.between(now, blockedUntil);
		long totalMinutes = Math.max(1, duration.toMinutes());
		long days = totalMinutes / (24 * 60);
		long hours = (totalMinutes % (24 * 60)) / 60;
		long minutes = totalMinutes % 60;

		StringBuilder remaining = new StringBuilder();
		if (days > 0) {
			remaining.append(days).append("일 ");
		}
		if (hours > 0) {
			remaining.append(hours).append("시간 ");
		}
		if (minutes > 0 || remaining.length() == 0) {
			remaining.append(minutes).append("분 ");
		}

		return "비정상 결제 시도가 감지되어 현재 결제를 진행할 수 없습니다. "
			+ remaining.toString().trim()
			+ " 후 다시 시도해 주세요.";
	}
}
