package org.truve.platform.ticketing.service.booking.external.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class TicketingEventCommandTest {
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void HOLD_REQUESTED_JSON_계약을_유지한다() throws Exception {
		LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 27, 12, 30, 15);
		TicketingEventCommand.HoldRequested event = TicketingEventCommand.HoldRequested.of(
			"H-001",
			"R-001",
			UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
			"session-token",
			100L,
			List.of(10L, 11L),
			expiresAt
		);

		String payload = objectMapper.writeValueAsString(event);
		JsonNode json = objectMapper.readTree(payload);

		assertThat(json.get("holdId").asText()).isEqualTo("H-001");
		assertThat(json.get("reservationNumber").asText()).isEqualTo("R-001");
		assertThat(json.get("scheduledSeatIds")).hasSize(2);
		assertThat(json.get("expiresAt").isArray()).isTrue();
		assertThat(json.has("eventType")).isFalse();
		assertThat(json.has("messageKey")).isFalse();

		TicketingEventCommand.HoldRequested restored = objectMapper.readValue(
			payload, TicketingEventCommand.HoldRequested.class);
		assertThat(restored.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(restored.getScheduledSeatIds()).containsExactly(10L, 11L);
	}
}
