package com.truve.platform.musical.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.user.domain.entity.User;
import com.truve.platform.musical.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserSignedUpEventConsumerTest {

	@Mock
	private UserRepository userRepository;

	private UserSignedUpEventConsumer userSignedUpEventConsumer;

	@BeforeEach
	void setUp() {
		userSignedUpEventConsumer = new UserSignedUpEventConsumer(new ObjectMapper(), userRepository);
	}

	@Test
	@DisplayName("정상 회원가입 이벤트를 수신하면 musical service 유저를 생성한다.")
	void 회원가입_이벤트_수신_성공() throws Exception {
		UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
		String message = """
			{"userId":"11111111-1111-1111-1111-111111111111","nickname":"test@test.com"}
			""";

		given(userRepository.existsByUserId(userId)).willReturn(false);

		userSignedUpEventConsumer.consume(message);

		ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);

		verify(userRepository).save(savedUserCaptor.capture());
		assertThat(savedUserCaptor.getValue().getUserId()).isEqualTo(userId);
		assertThat(savedUserCaptor.getValue().getNickname()).isEqualTo("test@test.com");
	}

	@Test
	@DisplayName("이미 생성된 유저면 저장을 건너뛴다.")
	void 회원가입_이벤트_중복_유저_건너뜀() throws Exception {
		String message = """
			{"userId":"11111111-1111-1111-1111-111111111111","nickname":"test@test.com"}
			""";

		given(userRepository.existsByUserId(UUID.fromString("11111111-1111-1111-1111-111111111111"))).willReturn(true);

		userSignedUpEventConsumer.consume(message);

		verify(userRepository, never()).save(any(User.class));
	}

	@Test
	@DisplayName("userId 또는 nickname 이 올바르지 않으면 예외가 발생한다.")
	void 회원가입_이벤트_유효성_실패() {
		String message = """
			{"userId":"invalid-uuid","nickname":""}
			""";

		CustomException exception = assertThrows(
			CustomException.class,
			() -> userSignedUpEventConsumer.consume(message)
		);

		assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EVENT_USER_SIGNED_UP_FAILED);
		verify(userRepository, never()).save(any(User.class));
	}
}
