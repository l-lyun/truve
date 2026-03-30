package com.truve.platform.payment.service.domain.command;

import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CancelCommand {
	private final Long amount;
	private final String reason;
	private final LocalDateTime canceledAt;
	private final String idempotencyKey;
	private final String status;
}
