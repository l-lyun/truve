package org.truve.platform.ticketing.service.ticketing.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class TicketingSecurityServiceTest {

	@Mock
	private TicketingRedisRepository ticketingRedisRepository;

	@InjectMocks
	private TicketingSecurityService ticketingSecurityService;

	@Test
	@DisplayName("매크로 차단 키가 있으면 세션 토큰을 만료시키고 SUSPECTED_MACRO_ACTIVITY 예외를 던진다.")
	void 매크로탐지_차단성공() {
		// given
		String sessionToken = "session-token";
		given(ticketingRedisRepository.validateMacro(sessionToken)).willReturn("blocked");

		// when
		CustomException exception = assertThrows(
			CustomException.class,
			() -> ticketingSecurityService.findMacro(sessionToken)
		);

		// then
		assertAll(
			() -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SUSPECTED_MACRO_ACTIVITY),
			() -> verify(ticketingRedisRepository).expireSessionToken(sessionToken)
		);
	}

	@Test
	@DisplayName("매크로 차단 키가 없으면 정상 통과하고 세션 토큰을 만료시키지 않는다.")
	void 매크로탐지_정상통과() {
		// given
		String sessionToken = "session-token";
		given(ticketingRedisRepository.validateMacro(sessionToken)).willReturn(null);

		// when
		assertDoesNotThrow(() -> ticketingSecurityService.findMacro(sessionToken));

		// then
		verify(ticketingRedisRepository, never()).expireSessionToken(anyString());
	}
}
