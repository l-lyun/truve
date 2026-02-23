package org.truve.platform.queue.service.queue.scheduler;

import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.truve.platform.queue.service.queue.repository.QueueRedisRepository;
import org.truve.platform.queue.service.queue.service.QueueService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

	private final QueueRedisRepository queueRedisRepository;
	private final QueueService queueService;

	// TODO: 스케줄링 기준 고도화
	@Scheduled(fixedDelayString = "500")
	public void promote() {
		Set<String> shows = queueRedisRepository.getShows();
		for (String showId : shows) {
			queueService.promoteWaitingUsers(showId);
		}
	}
}
