package org.truve.platform.ticketing.service.booking.risk.domain.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.truve.platform.ticketing.service.booking.risk.dto.BeBotRiskReportRequest;

import com.truve.platform.common.support.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "booking_bot_risk_summary")
public class BookingBotRiskSummary extends BaseEntity {

	@Column(nullable = false, unique = true)
	private UUID userId;

	@Column(nullable = false)
	private Integer riskCount;

	@Column
	private String lastOrderId;

	@Column
	private LocalDateTime blockedUntil;

	@Column(nullable = false)
	private Boolean extraAuthRequired;

	@Builder
	private BookingBotRiskSummary(
		UUID userId,
		Integer riskCount,
		String lastOrderId,
		LocalDateTime blockedUntil,
		Boolean extraAuthRequired
	) {
		this.userId = userId;
		this.riskCount = riskCount;
		this.lastOrderId = lastOrderId;
		this.blockedUntil = blockedUntil;
		this.extraAuthRequired = extraAuthRequired;
	}

	public static BookingBotRiskSummary create(UUID userId) {
		return BookingBotRiskSummary.builder()
			.userId(userId)
			.riskCount(0)
			.extraAuthRequired(false)
			.build();
	}

	public boolean isDuplicateOrder(String orderId) {
		return orderId != null && orderId.equals(lastOrderId);
	}

	public boolean isBlocked(LocalDateTime now) {
		return blockedUntil != null && blockedUntil.isAfter(now);
	}

	public void applyBotDetection(BeBotRiskReportRequest request, LocalDateTime now) {
		riskCount += 1;
		lastOrderId = request.getOrderId();

		if (riskCount >= 3) {
			extraAuthRequired = true;
		}

		if (riskCount == 4) {
			blockedUntil = now.plusHours(24);
		} else if (riskCount >= 5) {
			blockedUntil = now.plusDays(7);
		}
	}
}
