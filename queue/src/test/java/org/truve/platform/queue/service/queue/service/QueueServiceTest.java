package org.truve.platform.queue.service.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.queue.service.common.constants.QueueStatus;
import org.truve.platform.queue.service.common.exception.CustomException;
import org.truve.platform.queue.service.common.exception.ErrorCode;
import org.truve.platform.queue.service.queue.config.QueueProperties;
import org.truve.platform.queue.service.queue.dto.QueueResponse;
import org.truve.platform.queue.service.queue.jwt.JwtService;
import org.truve.platform.queue.service.queue.repository.QueueRedisRepository;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

	@Mock
	private QueueRedisRepository queueRedisRepository;

	@Mock
	private QueueProperties queueProperties;

	@Mock
	private JwtService jwtService;

	@Mock
	private QueuePollingPolicy queuePollingPolicy;

	@InjectMocks
	private QueueService queueService;

	@Nested
	@DisplayName("대기열 퇴장 테스트")
	class CancelTest {

		@Test
		@DisplayName("showId와 userId가 유효하면 대기열에서 제거한다.")
		void 대기열_퇴장_성공() {
			String showId = "1";
			String userId = "user-1";

			queueService.cancel(showId, userId);

			verify(queueRedisRepository).removeQueueMember(showId, userId);
		}

		@Test
		@DisplayName("showId가 비어 있으면 예외가 발생한다.")
		void 대기열_퇴장_실패_잘못된_공연() {
			CustomException exception = assertThrows(CustomException.class,
				() -> queueService.cancel("", "user-1"));

			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST_SHOW_ID);
			verify(queueRedisRepository, never()).removeQueueMember("","user-1");
		}

		@Test
		@DisplayName("userId가 비어 있으면 예외가 발생한다.")
		void 대기열_퇴장_실패_잘못된_유저() {
			CustomException exception = assertThrows(CustomException.class,
				() -> queueService.cancel("1", ""));

			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST_USER_ID);
			verify(queueRedisRepository, never()).removeQueueMember("1","");
		}
	}

	@Nested
	@DisplayName("대기열 상태 조회 테스트")
	class StatusTest {

		@Test
		@DisplayName("ready 토큰이 있으면 READY 상태를 반환한다.")
		void 대기열_상태조회_ready_성공() {
			String showId = "1";
			String userId = "user-1";

			given(queueRedisRepository.getReadyToken(showId, userId)).willReturn(Optional.of("ready-token"));
			given(queueRedisRepository.getWaitingUserCount(showId)).willReturn(10L);
			given(queueRedisRepository.getReadyTokenTtlSec(showId, userId)).willReturn(Optional.of(30L));
			given(queuePollingPolicy.forReady()).willReturn(1000L);

			QueueResponse.Status response = queueService.status(showId, userId);

			assertThat(response.getStatus()).isEqualTo(QueueStatus.READY);
			assertThat(response.getAdmissionToken()).isEqualTo("ready-token");
			assertThat(response.getExpireTime()).isEqualTo(30L);
			assertThat(response.getWaitingUserCount()).isEqualTo(10L);
			assertThat(response.getPollingMs()).isEqualTo(1000L);
		}

		@Test
		@DisplayName("대기열 순번이 있으면 WAITING 상태를 반환한다.")
		void 대기열_상태조회_waiting_성공() {
			String showId = "1";
			String userId = "user-1";

			given(queueRedisRepository.getReadyToken(showId, userId)).willReturn(Optional.empty());
			given(queueRedisRepository.getWaitingUserCount(showId)).willReturn(25L);
			given(queueRedisRepository.getRank(showId, userId)).willReturn(Optional.of(7L));
			given(queuePollingPolicy.forWaiting(7L)).willReturn(1500L);

			QueueResponse.Status response = queueService.status(showId, userId);

			assertThat(response.getStatus()).isEqualTo(QueueStatus.WAITING);
			assertThat(response.getRank()).isEqualTo(7L);
			assertThat(response.getWaitingUserCount()).isEqualTo(25L);
			assertThat(response.getPollingMs()).isEqualTo(1500L);
		}

		@Test
		@DisplayName("대기열 정보가 없으면 예외가 발생한다.")
		void 대기열_상태조회_실패_입장정보없음() {
			String showId = "1";
			String userId = "user-1";

			given(queueRedisRepository.getReadyToken(showId, userId)).willReturn(Optional.empty());
			given(queueRedisRepository.getWaitingUserCount(showId)).willReturn(0L);
			given(queueRedisRepository.getRank(showId, userId)).willReturn(Optional.empty());

			CustomException exception = assertThrows(CustomException.class,
				() -> queueService.status(showId, userId));

			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
		}
	}
}
