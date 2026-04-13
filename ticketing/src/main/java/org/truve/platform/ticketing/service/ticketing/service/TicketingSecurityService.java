package org.truve.platform.ticketing.service.ticketing.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.truve.platform.ticketing.service.ticketing.repository.TicketingRedisRepository;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class TicketingSecurityService {
	private final TicketingRedisRepository ticketingRedisRepository;

	public void findMacro(String sessionTicket) {
		if(StringUtils.hasText(ticketingRedisRepository.validateMacro(sessionTicket))) {
			ticketingRedisRepository.expireSessionToken(sessionTicket);
			throw new CustomException(ErrorCode.SUSPECTED_MACRO_ACTIVITY);
		}
	}
}
