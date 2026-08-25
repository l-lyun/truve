package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.outbox.OutboxStatus;
import com.truve.platform.common.support.JsonConverter;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	TicketingOutboxPublisher.class,
	JsonConverter.class,
	TicketingOutboxTransactionIntegrationTest.OutboxTransactionFacade.class,
	TicketingOutboxTransactionIntegrationTest.TestConfig.class
})
class TicketingOutboxTransactionIntegrationTest {

	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private OutboxTransactionFacade transactionFacade;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
		reservationRepository.deleteAll();
		savePendingPaymentReservation();
	}

	@Test
	void 결제완료시_예약과_티켓과_Outbox가_한_트랜잭션에_커밋된다() {
		transactionFacade.confirmAndRecord("R-001", false);

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED);
		assertThat(outboxRepository.findAll())
			.singleElement()
			.satisfies(event -> {
				assertThat(event.getMessageKey()).isEqualTo("R-001");
				assertThat(event.getEventType()).isEqualTo("SOLD_CONFIRMED");
				assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
			});
	}

	@Test
	void Outbox_기록후_트랜잭션이_실패하면_예약과_티켓과_Outbox가_함께_롤백된다() {
		assertThatThrownBy(() -> transactionFacade.confirmAndRecord("R-001", true))
			.isInstanceOf(IllegalStateException.class);

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING);
		assertThat(outboxRepository.count()).isZero();
	}

	private void savePendingPaymentReservation() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			Reservation reservation = Reservation.create(
				UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
				"R-001",
				"VIP석 1인",
				ShowInfo.builder()
					.showId(1L)
					.showScheduleId(100L)
					.title("공연")
					.venueName("공연장")
					.startAt(LocalDateTime.now().plusDays(30))
					.posterImg("poster.jpg")
					.build()
			);
			reservation.addTickets(List.of(Ticket.create(
				reservation, "T-001", "VIP", 10000L, "1층 A구역 1열 1번", 10L
			)));
			reservation.readyForPayment(null);
			reservationRepository.saveAndFlush(reservation);
		});
	}

	static class OutboxTransactionFacade {
		private final ReservationRepository reservationRepository;
		private final TicketingOutboxPublisher outboxPublisher;

		OutboxTransactionFacade(
			ReservationRepository reservationRepository,
			TicketingOutboxPublisher outboxPublisher
		) {
			this.reservationRepository = reservationRepository;
			this.outboxPublisher = outboxPublisher;
		}

		@Transactional
		public void confirmAndRecord(String reservationNumber, boolean failAfterRecording) {
			Reservation reservation = reservationRepository.findByNumber(reservationNumber);
			reservation.confirm(LocalDateTime.now(), LocalDateTime.now(), "카드", null);
			List<Long> scheduledSeatIds = reservation.getTickets().stream()
				.map(Ticket::getScheduledSeatId)
				.toList();
			outboxPublisher.publish(TicketingEventCommand.SoldConfirmed.of(reservation, scheduledSeatIds));
			if (failAfterRecording) {
				throw new IllegalStateException("transaction rollback");
			}
		}
	}

	@TestConfiguration
	static class TestConfig {
		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper();
		}
	}
}
