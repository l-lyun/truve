package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class TicketingOutboxMessageRelayTest {
	@Mock
	private KafkaTemplate<String, String> kafkaTemplate;
	@Mock
	private CompletableFuture<SendResult<String, String>> interruptedFuture;
	@InjectMocks
	private TicketingOutboxMessageRelay relay;

	@Test
	void Relay_스레드가_중단되면_확인하지_못한_claim을_실패처리하지_않는다() throws Exception {
		CompletableFuture<SendResult<String, String>> completedFuture = CompletableFuture.completedFuture(null);
		given(kafkaTemplate.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any()))
			.willReturn(interruptedFuture)
			.willReturn(completedFuture);
		given(interruptedFuture.get()).willThrow(new InterruptedException("shutdown"));

		try {
			List<OutboxRelayResult> results = relay.relay(List.of(claimed(1L), claimed(2L)));

			assertThat(results).isEmpty();
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
			verify(kafkaTemplate, times(2))
				.send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any());
		} finally {
			Thread.interrupted();
		}
	}

	private ClaimedOutboxEvent claimed(long id) {
		return new ClaimedOutboxEvent(
			id,
			"booking.ticketing",
			"R-00" + id,
			"{}",
			"SOLD_CONFIRMED",
			UUID.randomUUID()
		);
	}
}
