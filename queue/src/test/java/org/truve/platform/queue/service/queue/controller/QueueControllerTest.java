package org.truve.platform.queue.service.queue.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.queue.service.common.constants.QueueStatus;
import org.truve.platform.queue.service.common.response.ApiResult;
import org.truve.platform.queue.service.queue.dto.QueueResponse;
import org.truve.platform.queue.service.queue.service.QueueService;

@ExtendWith(MockitoExtension.class)
class QueueControllerTest {

	@Mock
	private QueueService queueService;

	@InjectMocks
	private QueueController queueController;

	@Test
	@DisplayName("대기열 진입 요청을 서비스에 위임하고 성공 응답을 반환한다.")
	void 대기열_진입_성공() {
		String showId = "1";
		String userId = "user-1";

		ApiResult<Void> response = queueController.enter(showId, userId);

		verify(queueService).enter(showId, userId);
		assertThat(response.getCode()).isEqualTo("ok");
		assertThat(response.getMessage()).isEqualTo("성공");
		assertThat(response.getData()).isNull();
	}

	@Test
	@DisplayName("대기열 상태 조회 요청을 서비스에 위임하고 응답을 반환한다.")
	void 대기열_상태조회_성공() {
		String showId = "1";
		String userId = "user-1";
		QueueResponse.Status status = QueueResponse.Status.wait(5L, 20L, 1500L);

		given(queueService.status(showId, userId)).willReturn(status);

		ApiResult<QueueResponse.Status> response = queueController.status(showId, userId);

		verify(queueService).status(showId, userId);
		assertThat(response.getCode()).isEqualTo("ok");
		assertThat(response.getMessage()).isEqualTo("성공");
		assertThat(response.getData()).isEqualTo(status);
		assertThat(response.getData().getStatus()).isEqualTo(QueueStatus.WAITING);
	}

	@Test
	@DisplayName("대기열 취소 요청을 서비스에 위임하고 성공 응답을 반환한다.")
	void 대기열_취소_성공() {
		String showId = "1";
		String userId = "user-1";

		ApiResult<Void> response = queueController.cancel(showId, userId);

		verify(queueService).cancel(showId, userId);
		assertThat(response.getCode()).isEqualTo("ok");
		assertThat(response.getMessage()).isEqualTo("성공");
		assertThat(response.getData()).isNull();
	}
}
