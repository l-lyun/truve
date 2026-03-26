package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.domain.entity.ShowScheduled;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingInternalResponse;
import org.truve.platform.ticketing.service.ticketing.repository.ScheduledSeatRepository;
import org.truve.platform.ticketing.service.ticketing.repository.ShowScheduledRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class TicketingInternalServiceTest {

	@Mock
	private ScheduledSeatRepository scheduledSeatRepository;

	@Mock
	private ShowScheduledRepository showScheduledRepository;

	@InjectMocks
	private TicketingInternalService ticketingInternalService;

	@Test
	@DisplayName("공연 회차가 존재하면 등급별 잔여 좌석 수를 반환한다.")
	void 등급별_잔여좌석_조회_성공() {
		// given
		Long showScheduleId = 1L;
		ShowScheduled showScheduled = ShowScheduled.builder()
			.title("지킬앤하이드")
			.venueName("블루스퀘어")
			.startAt(LocalDateTime.of(2026, 3, 26, 19, 0))
			.build();
		ReflectionTestUtils.setField(showScheduled, "id", showScheduleId);

		TicketingInternalResponse.FlatRemainingSeatInfo vipInfo = flatRemainingSeatInfo("VIP", 10L, 20L);
		TicketingInternalResponse.FlatRemainingSeatInfo rInfo = flatRemainingSeatInfo("R", 30L, 50L);

		given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.of(showScheduled));
		given(scheduledSeatRepository.findGradeRemainingSeats(showScheduleId, SeatStatus.AVAILABLE))
			.willReturn(List.of(vipInfo, rInfo));

		// when
		TicketingInternalResponse.RemainingSeats response = ticketingInternalService.getRemainingSeats(showScheduleId);

		// then
		assertAll(
			() -> assertThat(response.getShowScheduleId()).isEqualTo(showScheduleId),
			() -> assertThat(response.getGrades()).hasSize(2),
			() -> assertThat(response.getGrades().get(0).getGradeName()).isEqualTo("VIP"),
			() -> assertThat(response.getGrades().get(0).getRemainingSeatCount()).isEqualTo(10L),
			() -> assertThat(response.getGrades().get(0).getTotalCount()).isEqualTo(20L),
			() -> assertThat(response.getGrades().get(1).getGradeName()).isEqualTo("R"),
			() -> assertThat(response.getGrades().get(1).getRemainingSeatCount()).isEqualTo(30L),
			() -> assertThat(response.getGrades().get(1).getTotalCount()).isEqualTo(50L)
		);
		verify(scheduledSeatRepository).findGradeRemainingSeats(showScheduleId, SeatStatus.AVAILABLE);
	}

	@Test
	@DisplayName("공연 회차가 존재하지 않으면 INVALID_SHOW_SCHEDULE 예외가 발생한다.")
	void 등급별_잔여좌석_조회_공연없음() {
		// given
		Long showScheduleId = 1L;
		given(showScheduledRepository.findById(showScheduleId)).willReturn(Optional.empty());

		// when
		CustomException exception = assertThrows(
			CustomException.class,
			() -> ticketingInternalService.getRemainingSeats(showScheduleId)
		);

		// then
		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SHOW_SCHEDULE);
		verifyNoInteractions(scheduledSeatRepository);
	}

	private TicketingInternalResponse.FlatRemainingSeatInfo flatRemainingSeatInfo(
		String gradeName,
		Long remainingSeatCount,
		Long totalCount
	) {
		TicketingInternalResponse.FlatRemainingSeatInfo info =
			mock(TicketingInternalResponse.FlatRemainingSeatInfo.class);
		given(info.getGradeName()).willReturn(gradeName);
		given(info.getRemainingSeatCount()).willReturn(remainingSeatCount);
		given(info.getTotalCount()).willReturn(totalCount);
		return info;
	}
}
