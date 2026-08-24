package org.truve.platform.ticketing.service.booking.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

class TicketTest {

	@Test
	@DisplayName("티켓은 발급 대기 상태로 생성된다.")
	void 티켓생성_발급대기() {
		Ticket ticket = createTicket();

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.PENDING);
	}

	@Test
	@DisplayName("발급 대기 중인 티켓을 발급한다.")
	void 티켓발급_성공() {
		Ticket ticket = createTicket();

		ticket.issue();

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ISSUED);
	}

	@Test
	@DisplayName("취소된 티켓은 다시 발급할 수 없다.")
	void 취소티켓_발급실패() {
		Ticket ticket = createTicket();
		ticket.cancel(LocalDateTime.now());

		assertThatThrownBy(ticket::issue)
			.isInstanceOf(CustomException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.INVALID_TICKET_STATUS);
	}

	private Ticket createTicket() {
		return Ticket.create(null, "T-001", "VIP", 120000L, "1층 A구역 1열 1번", 1L);
	}
}
