package org.truve.platform.queue.service.queue.service;

import org.springframework.stereotype.Component;

@Component
public class QueuePollingPolicy {

	// TODO: 입장 순위에 따른 폴링 주기 정책 필요
	private static final long READY_POLL_AFTER_MS = 1000L;
	private static final long WAIT_POLL_FAST_MS = 1500L;
	private static final long WAIT_POLL_MEDIUM_MS = 3000L;
	private static final long WAIT_POLL_SLOW_MS = 5000L;
	private static final long FAST_RANK_THRESHOLD = 100L;
	private static final long MEDIUM_RANK_THRESHOLD = 1000L;

	public long forReady() {
		return READY_POLL_AFTER_MS;
	}

	public long forWaiting(long rank) {
		if (rank <= FAST_RANK_THRESHOLD) {
			return WAIT_POLL_FAST_MS;
		}
		if (rank <= MEDIUM_RANK_THRESHOLD) {
			return WAIT_POLL_MEDIUM_MS;
		}
		return WAIT_POLL_SLOW_MS;
	}
}
