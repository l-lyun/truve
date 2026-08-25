package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.truve.platform.ticketing.service.booking.domain.constant.ReservationStatus;
import org.truve.platform.ticketing.service.booking.domain.constant.TicketStatus;
import org.truve.platform.ticketing.service.booking.domain.entity.Reservation;
import org.truve.platform.ticketing.service.booking.domain.entity.Ticket;
import org.truve.platform.ticketing.service.booking.domain.entity.embedded.ShowInfo;
import org.truve.platform.ticketing.service.booking.dto.BookingRequest;
import org.truve.platform.ticketing.service.booking.external.client.payment.PaymentClient;
import org.truve.platform.ticketing.service.booking.external.kafka.BookingEventCommand;
import org.truve.platform.ticketing.service.booking.external.kafka.PaymentPublisher;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.booking.outbox.repository.TicketingOutboxEventRepository;
import org.truve.platform.ticketing.service.booking.repository.ReservationRepository;
import org.truve.platform.ticketing.service.booking.risk.service.BookingBotRiskService;
import org.truve.platform.ticketing.service.booking.service.BookingCreationService;
import org.truve.platform.ticketing.service.booking.service.BookingLockService;
import org.truve.platform.ticketing.service.booking.service.BookingService;
import org.truve.platform.ticketing.service.ticketing.service.SeatHoldLockService;
import org.truve.platform.ticketing.service.ticketing.service.SeatHoldService;

import com.truve.platform.common.support.JsonConverter;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
	BookingService.class,
	TicketingOutboxPublisher.class
})
class BookingServiceOutboxIntegrationTest {

	@Autowired
	private BookingService bookingService;
	@Autowired
	private ReservationRepository reservationRepository;
	@Autowired
	private TicketingOutboxEventRepository outboxRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private TicketingOutboxPublisher outboxPublisher;
	@MockitoBean
	private BookingCreationService bookingCreationService;
	@MockitoBean
	private BookingLockService bookingLockService;
	@MockitoBean
	private SeatHoldService seatHoldService;
	@MockitoBean
	private SeatHoldLockService seatHoldLockService;
	@MockitoBean
	private PaymentPublisher paymentPublisher;
	@MockitoBean
	private PaymentClient paymentClient;
	@MockitoBean
	private BookingBotRiskService bookingBotRiskService;
	@MockitoBean
	private JsonConverter jsonConverter;

	@BeforeEach
	void setUp() {
		outboxRepository.deleteAll();
		reservationRepository.deleteAll();
		savePendingPaymentReservation();
		given(jsonConverter.serialize(any())).willReturn("{}");
	}

	@Test
	void 실제_BookingService가_예약과_티켓과_Outbox를_함께_커밋하고_중복은_추가하지_않는다() {
		BookingEventCommand.Confirmed event = confirmedEvent();

		bookingService.confirm(event);
		bookingService.confirm(event);

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED);
		assertThat(outboxRepository.findAll())
			.singleElement()
			.satisfies(outbox -> {
				assertThat(outbox.getMessageKey()).isEqualTo("R-001");
				assertThat(outbox.getEventType()).isEqualTo("SOLD_CONFIRMED");
			});
	}

	@Test
	void 실제_BookingService에서_Outbox_기록이_실패하면_예약과_티켓도_롤백된다() {
		given(jsonConverter.serialize(any()))
			.willThrow(new IllegalStateException("outbox failure"));

		assertThatThrownBy(() -> bookingService.confirm(confirmedEvent()))
			.isInstanceOf(IllegalStateException.class);

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.PENDING);
		assertThat(outboxRepository.count()).isZero();
	}

	@Test
	void 가상계좌_입금완료를_중복처리해도_실제_Outbox는_한_건만_남는다() {
		LocalDateTime now = LocalDateTime.now();
		bookingService.confirm(new BookingEventCommand.Confirmed(
			"R-001",
			now,
			null,
			"가상계좌",
			new BookingEventCommand.Confirmed.VirtualAccount(
				"111-111", "은행", "홍길동", now.plusDays(1)
			)
		));

		bookingService.depositReceive(new BookingEventCommand.DepositReceived("R-001", now));
		bookingService.depositReceive(new BookingEventCommand.DepositReceived("R-001", now));

		Reservation saved = reservationRepository.findByNumber("R-001");
		assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(saved.getTickets()).allMatch(ticket -> ticket.getStatus() == TicketStatus.ISSUED);
		assertThat(outboxRepository.findAll())
			.singleElement()
			.extracting(outbox -> outbox.getEventType())
			.isEqualTo("SOLD_CONFIRMED");
	}

	@Test
	void 결제확정후_취소하면_SOLD_CONFIRMED_다음에_SALE_CANCELED가_기록된다() {
		bookingService.confirm(confirmedEvent());
		Reservation confirmed = reservationRepository.findByNumber("R-001");
		Long ticketId = confirmed.getTickets().getFirst().getId();

		bookingService.cancel(
			"R-001",
			new BookingRequest.Cancel("단순변심", List.of(ticketId))
		);

		assertThat(outboxRepository.findAll().stream()
			.sorted(java.util.Comparator.comparing(event -> event.getId()))
			.map(event -> event.getEventType())
			.toList())
			.containsExactly("SOLD_CONFIRMED", "SALE_CANCELED");
	}

	@Test
	void OutboxPublisher를_트랜잭션_밖에서_호출하면_실패한다() {
		Reservation reservation = reservationRepository.findByNumber("R-001");
		TicketingEventCommand.SoldConfirmed command = TicketingEventCommand.SoldConfirmed.of(
			reservation,
			reservation.getTickets().stream().map(Ticket::getScheduledSeatId).toList()
		);

		assertThatThrownBy(() -> outboxPublisher.publish(command))
			.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(outboxRepository.count()).isZero();
	}

	private BookingEventCommand.Confirmed confirmedEvent() {
		return new BookingEventCommand.Confirmed(
			"R-001", LocalDateTime.now(), LocalDateTime.now(), "카드", null
		);
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
}
