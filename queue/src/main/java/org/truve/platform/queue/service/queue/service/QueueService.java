package org.truve.platform.queue.service.queue.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.truve.platform.queue.service.common.exception.CustomException;
import org.truve.platform.queue.service.common.exception.ErrorCode;
import org.truve.platform.queue.service.common.support.Preconditions;
import org.truve.platform.queue.service.queue.config.QueueProperties;
import org.truve.platform.queue.service.queue.dto.QueueResponse;
import org.truve.platform.queue.service.queue.jwt.JwtService;
import org.truve.platform.queue.service.queue.repository.QueueRedisRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

	private final QueueRedisRepository queueRedisRepository;
	private final QueueProperties queueProperties;
	private final JwtService jwtService;

	public void enter(String showId, String userId) {

		Preconditions.validate(StringUtils.hasText(showId), ErrorCode.INVALID_REQUEST_SHOW_ID);
		Preconditions.validate(StringUtils.hasText(userId), ErrorCode.INVALID_REQUEST_USER_ID);

		long now = System.currentTimeMillis();
		queueRedisRepository.registerShow(showId);
		queueRedisRepository.enqueue(showId, userId, now);
	}

	public QueueResponse.Status status(String showId, String userId) {

		Preconditions.validate(StringUtils.hasText(showId), ErrorCode.INVALID_REQUEST_SHOW_ID);
		Preconditions.validate(StringUtils.hasText(userId), ErrorCode.INVALID_REQUEST_USER_ID);

		var readyToken = queueRedisRepository.getReadyToken(showId, userId);
		Long waitingUserCount = queueRedisRepository.getWaitingUserCount(showId);

		if (readyToken.isPresent()) {
			long ttl = queueRedisRepository.getReadyTokenTtlSec(showId, userId)
				.orElse(queueProperties.getReadyTtlSec());
			return QueueResponse.Status.ready(readyToken.get(), ttl, waitingUserCount);
		}

		var rank = queueRedisRepository.getRank(showId, userId);
		if (rank.isPresent()) {
			return QueueResponse.Status.wait(rank.get(), waitingUserCount);
		}

		throw new CustomException(ErrorCode.QUEUE_ENTRY_NOT_FOUND);
	}


	public int promoteWaitingUsers(String showId) {

		Preconditions.validate(StringUtils.hasText(showId), ErrorCode.INVALID_REQUEST_SHOW_ID);

		int permit = queueProperties.getPermitPerTick();
		long ttlSec = queueProperties.getReadyTtlSec();

		List<String> users = queueRedisRepository.popWaitingUsers(showId, permit);
		for (String userId : users) {
			String admissionToken = jwtService.issue(showId, userId, ttlSec);
			queueRedisRepository.saveReadyToken(showId, userId, admissionToken, queueProperties.getReadyTtlSec());
		}
		return users.size();
	}

}
