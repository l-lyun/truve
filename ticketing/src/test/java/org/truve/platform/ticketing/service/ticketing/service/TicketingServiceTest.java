package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.global.jwt.AdmissionTokenService;
import org.truve.platform.ticketing.service.ticketing.config.TicketingProperties;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ScheduledSeat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.Seat;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.dto.AdmissionTokenClaimsDTO;
import org.truve.platform.ticketing.service.ticketing.dto.SeatSectionsDto;
import org.truve.platform.ticketing.service.ticketing.dto.SessionTicketValueDTO;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class TicketingServiceTest {

	@Mock
	private TicketingRedisRepository ticketingRedisRepository;
	@Mock
	private AdmissionTokenService admissionTokenService;
	@Mock
	private TicketingProperties ticketingProperties;
	@Mock
	private ScheduledSeatRepository scheduledSeatRepository;
	@Mock
	private ShowScheduledRepository showScheduledRepository;
	@Mock
	private TicketingSecurityService ticketingSecurityService;

	@InjectMocks
	private TicketingService ticketingService;

	private Long showScheduleId;
	private UUID userId;
	private String admissionToken;
	private String sessionToken;

	@BeforeEach
	void setUp() {
		showScheduleId = 1L;
		userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		admissionToken = "admission-token";
		sessionToken = "session-token";
		lenient().when(ticketingProperties.getSessionTtlSec()).thenReturn(300L);
	}

	@Nested
	@DisplayName("입장 테스트")
	class EnterTest {

		@Test
		@DisplayName("입장 토큰이 유효하면 세션 토큰과 TTL을 반환한다.")
		void 입장_성공() {
			// given
			AdmissionTokenClaimsDTO claims = AdmissionTokenClaimsDTO.of(userId, showScheduleId, "admission");
			given(admissionTokenService.parseAdmissionToken(admissionToken, showScheduleId, userId))
				.willReturn(claims);
			given(ticketingRedisRepository.consumeAdmissionToken(showScheduleId, userId, admissionToken))
				.willReturn(true);
			given(ticketingRedisRepository.getSessionTokenTtl(anyString())).willReturn(300_000L);

			// when
			TicketingResponse.Enter response = ticketingService.enter(showScheduleId, userId, admissionToken);

			// then
			assertAll(
				() -> assertThat(response.getSessionToken()).isNotBlank(),
				() -> assertThat(response.getExpireIn()).isEqualTo(300_000L),
				() -> verify(ticketingRedisRepository).saveSessionToken(anyString(), eq(userId), eq(showScheduleId),
					eq(Duration.ofMinutes(5))),
				() -> verify(ticketingRedisRepository).addActiveTicketingUser(eq(showScheduleId), anyString())
			);
		}

		@Test
		@DisplayName("입장 토큰 소비에 실패하면 예외가 발생한다.")
		void 입장_토큰실패() {
			// given
			AdmissionTokenClaimsDTO claims = AdmissionTokenClaimsDTO.of(userId, showScheduleId, "admission");
			given(admissionTokenService.parseAdmissionToken(admissionToken, showScheduleId, userId))
				.willReturn(claims);
			given(ticketingRedisRepository.consumeAdmissionToken(showScheduleId, userId, admissionToken))
				.willReturn(false);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.enter(showScheduleId, userId, admissionToken)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADMISSION_TOKEN);
		}
	}

	@Nested
	@DisplayName("하트비트 테스트")
	class HeartbeatTest {

		@Test
		@DisplayName("세션 토큰이 유효하면 활성 사용자 갱신과 TTL 연장을 수행한다.")
		void 하트비트_성공() {
			// given
			given(ticketingProperties.getActiveWindowMs()).willReturn(30_000L);
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(userId, showScheduleId));
			given(ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, 300L)).willReturn(true);

			// when
			ticketingService.heartbeat(showScheduleId, userId, sessionToken);

			// then
			assertAll(
				() -> verify(ticketingRedisRepository).addActiveTicketingUser(showScheduleId, sessionToken),
				() -> verify(ticketingRedisRepository).removeInactiveTicketingUsers(eq(showScheduleId), anyLong()),
				() -> verify(ticketingRedisRepository).refreshSessionTokenTtl(sessionToken, 300L)
			);
		}

		@Test
		@DisplayName("세션 값이 없으면 INVALID_SESSION_TOKEN 예외가 발생한다.")
		void 하트비트_세션없음() {
			// given

			given(ticketingRedisRepository.getSessionTokenValue(sessionToken)).willReturn(null);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.heartbeat(showScheduleId, userId, sessionToken)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SESSION_TOKEN);
		}

		@Test
		@DisplayName("세션의 사용자 정보가 다르면 SESSION_TOKEN_MISMATCH 예외가 발생한다.")
		void 하트비트_유저불일치() {
			// given
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(UUID.fromString("22222222-2222-2222-2222-222222222222"), showScheduleId));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.heartbeat(showScheduleId, userId, sessionToken)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_TOKEN_MISMATCH);
		}

		@Test
		@DisplayName("세션의 공연 정보가 다르면 SESSION_TOKEN_MISMATCH 예외가 발생한다.")
		void 하트비트_공연불일치() {
			// given
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(userId, 999L));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.heartbeat(showScheduleId, userId, sessionToken)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_TOKEN_MISMATCH);
		}

		@Test
		@DisplayName("세션 TTL 연장에 실패하면 INVALID_SESSION_TOKEN 예외가 발생한다.")
		void 하트비트_TTL연장실패() {
			// given
			given(ticketingProperties.getActiveWindowMs()).willReturn(30_000L);
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(userId, showScheduleId));
			given(ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, 300L)).willReturn(false);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.heartbeat(showScheduleId, userId, sessionToken)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SESSION_TOKEN);
		}
	}

	@Nested
	@DisplayName("좌석 선점 테스트")
	class HoldSeatTest {

		private ShowScheduled showScheduled;
		private ScheduledSeat scheduledSeat1;
		private ScheduledSeat scheduledSeat2;

		@BeforeEach
		void setUpHoldSeat() {
			lenient().when(ticketingProperties.getActiveWindowMs()).thenReturn(30_000L);
			lenient().when(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.thenReturn(SessionTicketValueDTO.of(userId, showScheduleId));
			lenient().when(ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, 300L)).thenReturn(true);

			showScheduled = ShowScheduled.builder()
				.title("공연")
				.venueName("공연장")
				.startAt(LocalDateTime.of(2026, 3, 6, 20, 0))
				.build();
			ReflectionTestUtils.setField(showScheduled, "id", showScheduleId);

			scheduledSeat1 = createScheduledSeat(10L, showScheduleId, SeatStatus.AVAILABLE);
			scheduledSeat2 = createScheduledSeat(11L, showScheduleId, SeatStatus.AVAILABLE);
			ReflectionTestUtils.setField(scheduledSeat1.getSeat(), "id", 100L);
			ReflectionTestUtils.setField(scheduledSeat2.getSeat(), "id", 101L);
		}

		@Test
		@DisplayName("유효한 좌석 목록이면 모두 선점한다.")
		void 좌석선점_성공() {
			// given
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
			given(scheduledSeatRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(scheduledSeat1, scheduledSeat2));
			given(ticketingRedisRepository.tryHoldSeat(showScheduleId, 10L, sessionToken)).willReturn(true);
			given(ticketingRedisRepository.tryHoldSeat(showScheduleId, 11L, sessionToken)).willReturn(true);

			// when
			ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L, 11L));

			// then
			assertAll(
				() -> verify(ticketingSecurityService).findMacro(sessionToken),
				() -> verify(ticketingRedisRepository).tryHoldSeat(showScheduleId, 10L, sessionToken),
				() -> verify(ticketingRedisRepository).tryHoldSeat(showScheduleId, 11L, sessionToken)
			);
		}

		@Test
		@DisplayName("매크로 탐지 대상 세션이면 SUSPECTED_MACRO_ACTIVITY 예외가 발생하고 선점 로직은 수행하지 않는다.")
		void 좌석선점_매크로탐지() {
			// given
			willThrow(new CustomException(ErrorCode.SUSPECTED_MACRO_ACTIVITY))
				.given(ticketingSecurityService).findMacro(sessionToken);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L))
			);

			// then
			assertAll(
				() -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUSPECTED_MACRO_ACTIVITY),
				() -> verify(ticketingSecurityService).findMacro(sessionToken),
				() -> verify(showScheduledRepository, never()).findById(anyLong()),
				() -> verify(scheduledSeatRepository, never()).findAllById(anyIterable()),
				() -> verify(ticketingRedisRepository, never()).tryHoldSeat(anyLong(), anyLong(), anyString())
			);
		}

		@Test
		@DisplayName("최대 좌석 수를 초과하면 EXCEEDED_MAX_TICKET_COUNT 예외가 발생한다.")
		void 좌석선점_최대수초과() {
			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(1L, 2L, 3L, 4L, 5L))
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXCEEDED_MAX_TICKET_COUNT);
		}

		@Test
		@DisplayName("공연이 존재하지 않으면 INVALID_SHOW_SCHEDULE 예외가 발생한다.")
		void 좌석선점_공연없음() {
			// given
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.empty());

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L))
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SHOW_SCHEDULE);
		}

		@Test
		@DisplayName("조회한 좌석 수가 요청 수와 다르면 NOT_CORRECT_SEAT 예외가 발생한다.")
		void 좌석선점_좌석개수불일치() {
			// given
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
			given(scheduledSeatRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(scheduledSeat1));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L, 11L))
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_SEAT);
		}

		@Test
		@DisplayName("다른 공연의 좌석이면 NOT_CORRECT_SEAT 예외가 발생한다.")
		void 좌석선점_다른공연좌석() {
			// given
			ScheduledSeat otherShowSeat = createScheduledSeat(10L, 999L, SeatStatus.AVAILABLE);

			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
			given(scheduledSeatRepository.findAllById(List.of(10L))).willReturn(List.of(otherShowSeat));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L))
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_SEAT);
		}

		@Test
		@DisplayName("이미 판매된 좌석이면 ALREADY_SOLD_SEAT 예외가 발생한다.")
		void 좌석선점_이미판매됨() {
			// given
			ScheduledSeat soldSeat = createScheduledSeat(10L, showScheduleId, SeatStatus.SOLD);

			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
			given(scheduledSeatRepository.findAllById(List.of(10L))).willReturn(List.of(soldSeat));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L))
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_SOLD_SEAT);
		}

		@Test
		@DisplayName("다른 세션이 이미 선점한 좌석이면 ALREADY_HOLD_SEAT 예외가 발생한다.")
		void 좌석선점_다른세션선점() {
			// given
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
			given(scheduledSeatRepository.findAllById(List.of(10L))).willReturn(List.of(scheduledSeat1));
			given(ticketingRedisRepository.tryHoldSeat(showScheduleId, 10L, sessionToken)).willReturn(false);
			given(ticketingRedisRepository.getHoldSeatSessionToken(showScheduleId, 10L)).willReturn("other-session");

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L))
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_HOLD_SEAT);
		}

		@Test
		@DisplayName("같은 세션이 이미 선점한 좌석이면 그대로 통과한다.")
		void 좌석선점_같은세션재요청() {
			// given
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
			given(scheduledSeatRepository.findAllById(List.of(10L))).willReturn(List.of(scheduledSeat1));
			given(ticketingRedisRepository.tryHoldSeat(showScheduleId, 10L, sessionToken)).willReturn(false);
			given(ticketingRedisRepository.getHoldSeatSessionToken(showScheduleId, 10L)).willReturn(sessionToken);

			// when
			ticketingService.holdSeat(showScheduleId, userId, sessionToken, List.of(10L));

			// then
			verify(ticketingRedisRepository).getHoldSeatSessionToken(showScheduleId, 10L);
		}
	}

	@Nested
	@DisplayName("좌석 선점 취소 테스트")
	class CancelHoldSeatTest {

		@BeforeEach
		void setUpCancelHoldSeat() {
			given(ticketingProperties.getActiveWindowMs()).willReturn(30_000L);
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(userId, showScheduleId));
			given(ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, 300L)).willReturn(true);
		}

		@Test
		@DisplayName("같은 세션이 점유한 좌석이면 점유를 해제한다.")
		void 좌석선점취소_성공() {
			// given
			ScheduledSeat scheduledSeat1 = createScheduledSeat(10L, showScheduleId, SeatStatus.AVAILABLE);
			ScheduledSeat scheduledSeat2 = createScheduledSeat(11L, showScheduleId, SeatStatus.AVAILABLE);
			ReflectionTestUtils.setField(scheduledSeat1.getSeat(), "id", 100L);
			ReflectionTestUtils.setField(scheduledSeat2.getSeat(), "id", 101L);
			given(scheduledSeatRepository.findAllById(List.of(10L, 11L))).willReturn(List.of(scheduledSeat1, scheduledSeat2));
			given(ticketingRedisRepository.deleteHoldSeat(showScheduleId, 10L, sessionToken)).willReturn(true);
			given(ticketingRedisRepository.deleteHoldSeat(showScheduleId, 11L, sessionToken)).willReturn(true);

			// when
			ticketingService.cancelHoldSeat(showScheduleId, userId, sessionToken, List.of(10L, 11L));

			// then
			assertAll(
				() -> verify(ticketingRedisRepository).deleteHoldSeat(showScheduleId, 10L, sessionToken),
				() -> verify(ticketingRedisRepository).deleteHoldSeat(showScheduleId, 11L, sessionToken)
			);
		}

		@Test
		@DisplayName("다른 세션이 점유한 좌석이면 INVALID_HOLD_SEAT 예외가 발생한다.")
		void 좌석선점취소_타세션점유() {
			// given
			ScheduledSeat scheduledSeat = createScheduledSeat(10L, showScheduleId, SeatStatus.AVAILABLE);
			given(scheduledSeatRepository.findAllById(List.of(10L))).willReturn(List.of(scheduledSeat));
			given(ticketingRedisRepository.deleteHoldSeat(showScheduleId, 10L, sessionToken)).willReturn(false);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.cancelHoldSeat(showScheduleId, userId, sessionToken, List.of(10L))
			);

			// then
			assertAll(
				() -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_HOLD_SEAT),
				() -> verify(ticketingRedisRepository).deleteHoldSeat(showScheduleId, 10L, sessionToken)
			);
		}
	}

	@Nested
	@DisplayName("공연 조회 테스트")
	class GetShowTest {

		@BeforeEach
		void setUpShow() {
			given(ticketingProperties.getActiveWindowMs()).willReturn(30_000L);
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(userId, showScheduleId));
			given(ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, 300L)).willReturn(true);
		}

		@Test
		@DisplayName("공연 정보를 정상 반환한다.")
		void 공연조회_성공() {
			// given
			LocalDateTime startAt = LocalDateTime.of(2026, 3, 7, 19, 30);
			ShowScheduled showScheduled = ShowScheduled.builder()
				.title("지킬앤하이드")
				.venueName("블루스퀘어")
				.startAt(startAt)
				.build();
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));

			// when
			TicketingResponse.Show response = ticketingService.getShow(userId, showScheduleId, sessionToken);

			// then
			assertAll(
				() -> assertThat(response.getTitle()).isEqualTo("지킬앤하이드"),
				() -> assertThat(response.getVenueName()).isEqualTo("블루스퀘어"),
				() -> assertThat(response.getStartAt()).isEqualTo(startAt)
			);
		}

		@Test
		@DisplayName("공연 정보가 없으면 INVALID_SHOW_SCHEDULE 예외가 발생한다.")
		void 공연조회_공연없음() {
			// given
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.empty());

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.getShow(userId, showScheduleId, sessionToken)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SHOW_SCHEDULE);
		}
	}

	@Nested
	@DisplayName("티켓팅 퇴장 테스트")
	class ExitTicketingTest {

		@Test
		@DisplayName("정상 세션이면 세션 토큰을 만료하고 active ticketing user에서 제거한다.")
		void 티켓팅퇴장_성공() {
			// given
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(userId, showScheduleId));

			// when
			ticketingService.exitTicketing(showScheduleId, userId, sessionToken);

			// then
			assertAll(
				() -> verify(ticketingRedisRepository).expireSessionToken(sessionToken),
				() -> verify(ticketingRedisRepository).exitTicketing(showScheduleId, sessionToken)
			);
		}

		@Test
		@DisplayName("세션이 없으면 INVALID_SESSION_TOKEN 예외가 발생하고 퇴장 정리는 수행하지 않는다.")
		void 티켓팅퇴장_세션없음() {
			// given
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken)).willReturn(null);

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.exitTicketing(showScheduleId, userId, sessionToken)
			);

			// then
			assertAll(
				() -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SESSION_TOKEN),
				() -> verify(ticketingRedisRepository, never()).expireSessionToken(anyString()),
				() -> verify(ticketingRedisRepository, never()).exitTicketing(anyLong(), anyString())
			);
		}

		@Test
		@DisplayName("세션 사용자 정보가 다르면 SESSION_TOKEN_MISMATCH 예외가 발생하고 퇴장 정리는 수행하지 않는다.")
		void 티켓팅퇴장_사용자불일치() {
			// given
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(UUID.fromString("22222222-2222-2222-2222-222222222222"), showScheduleId));

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.exitTicketing(showScheduleId, userId, sessionToken)
			);

			// then
			assertAll(
				() -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SESSION_TOKEN_MISMATCH),
				() -> verify(ticketingRedisRepository, never()).expireSessionToken(anyString()),
				() -> verify(ticketingRedisRepository, never()).exitTicketing(anyLong(), anyString())
			);
		}
	}

	@Nested
	@DisplayName("좌석 목록 조회 테스트")
	class GetSeatsTest {

		@BeforeEach
		void setUpSeats() {
			given(ticketingProperties.getActiveWindowMs()).willReturn(30_000L);
			given(ticketingRedisRepository.getSessionTokenValue(sessionToken))
				.willReturn(SessionTicketValueDTO.of(userId, showScheduleId));
			given(ticketingRedisRepository.refreshSessionTokenTtl(sessionToken, 300L)).willReturn(true);
		}

		@Test
		@DisplayName("좌석 목록을 section-row-seat 구조로 변환해 반환한다.")
		void 좌석목록조회_성공() {
			// given
			ShowScheduled showScheduled = ShowScheduled.builder()
				.title("공연")
				.venueName("공연장")
				.startAt(LocalDateTime.now())
				.build();
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
			given(scheduledSeatRepository.findSeatSectionByScheduledSeatId(showScheduleId)).willReturn(List.of(
				new SeatSectionsDto(1L, "VIP", "VIP", 150000L, 10L, "A", 1L, SeatStatus.AVAILABLE),
				new SeatSectionsDto(1L, "VIP", "VIP", 150000L, 11L, "A", 2L, SeatStatus.SOLD),
				new SeatSectionsDto(1L, "VIP", "VIP", 150000L, 12L, "B", 1L, SeatStatus.AVAILABLE)
			));

			// when
			TicketingResponse.Seats response = ticketingService.getSeats(showScheduleId, userId, sessionToken);

			// then
			assertAll(
				() -> assertThat(response.getSections()).hasSize(1),
				() -> assertThat(response.getSections().get(0).getRows()).hasSize(2),
				() -> assertThat(response.getSections().get(0).getRows().get(0).getSeats()).hasSize(2),
				() -> assertThat(response.getSections().get(0).getRows().get(0).getSeats().get(1).getStatus())
					.isEqualTo(SeatStatus.SOLD)
			);
		}

		@Test
		@DisplayName("공연 정보가 없으면 INVALID_SHOW_SCHEDULE 예외가 발생한다.")
		void 좌석목록조회_공연없음() {
			// given
			given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.empty());

			// when
			CustomException exception = assertThrows(
				CustomException.class,
				() -> ticketingService.getSeats(showScheduleId, userId, sessionToken)
			);

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SHOW_SCHEDULE);
		}
	}

	private ScheduledSeat createScheduledSeat(Long seatId, Long scheduledShowId, SeatStatus status) {
		Seat seat = Seat.builder()
			.seatRow("A")
			.seatNumber(seatId)
			.build();
		ReflectionTestUtils.setField(seat, "id", seatId);

		ScheduledSeat scheduledSeat = ScheduledSeat.builder()
			.seat(seat)
			.showScheduleId(scheduledShowId)
			.build();
		ReflectionTestUtils.setField(scheduledSeat, "id", seatId);
		ReflectionTestUtils.setField(scheduledSeat, "status", status);

		return scheduledSeat;
	}
}
