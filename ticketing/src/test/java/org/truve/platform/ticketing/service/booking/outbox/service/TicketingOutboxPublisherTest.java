package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.domain.entity.TicketingOutboxEvent;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;

import com.truve.platform.common.outbox.OutboxStatus;
import com.truve.platform.common.support.JsonConverter;

@ExtendWith(MockitoExtension.class)
class TicketingOutboxPublisherTest {
	@Mock
	private JsonConverter jsonConverter;
	@Mock
	private TicketingOutboxEventRepository outboxRepository;
	@InjectMocks
	private TicketingOutboxPublisher publisher;

	@Test
	void SOLD_CONFIRMED를_Kafka로_보내지_않고_PENDING_Outbox로_저장한다() {
		TicketingEventCommand.SoldConfirmed command = TicketingEventCommand.SoldConfirmed.builder()
			.reservationNumber("R-001")
			.userId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
			.scheduledSeatIds(List.of(10L, 11L))
			.build();
		given(jsonConverter.serialize(command)).willReturn("{\"reservationNumber\":\"R-001\"}");

		publisher.publish(command);

		ArgumentCaptor<TicketingOutboxEvent> captor = ArgumentCaptor.forClass(TicketingOutboxEvent.class);
		verify(outboxRepository).save(captor.capture());
		TicketingOutboxEvent saved = captor.getValue();
		assertThat(saved.getTopic()).isEqualTo("booking.ticketing");
		assertThat(saved.getMessageKey()).isEqualTo("R-001");
		assertThat(saved.getEventType()).isEqualTo("SOLD_CONFIRMED");
		assertThat(saved.getPayload()).isEqualTo("{\"reservationNumber\":\"R-001\"}");
		assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
	}

	@Test
	void HOLD_REQUESTED는_예약번호를_Outbox_메시지_키로_저장한다() {
		LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 27, 12, 0);
		TicketingEventCommand.HoldRequested command = TicketingEventCommand.HoldRequested.of(
			"H-001",
			"R-001",
			UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
			"session-token",
			100L,
			List.of(10L, 11L),
			expiresAt
		);
		given(jsonConverter.serialize(command)).willReturn("{\"holdId\":\"H-001\"}");

		publisher.publish(command);

		ArgumentCaptor<TicketingOutboxEvent> captor = ArgumentCaptor.forClass(TicketingOutboxEvent.class);
		verify(outboxRepository).save(captor.capture());
		TicketingOutboxEvent saved = captor.getValue();
		assertThat(saved.getTopic()).isEqualTo("booking.ticketing");
		assertThat(saved.getMessageKey()).isEqualTo("R-001");
		assertThat(saved.getEventType()).isEqualTo("HOLD_REQUESTED");
		assertThat(saved.getPayload()).isEqualTo("{\"holdId\":\"H-001\"}");
		assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
	}

	@Test
	void 메시지_키가_없으면_Outbox를_저장하지_않는다() {
		TicketingEventCommand.TicketingEvent command = new TicketingEventCommand.TicketingEvent() {
			@Override
			public String getReservationNumber() {
				return null;
			}

			@Override
			public String getEventType() {
				return "INVALID";
			}
		};

		assertThatThrownBy(() -> publisher.publish(command))
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("outbox message key");
		verifyNoInteractions(jsonConverter, outboxRepository);
	}
}
