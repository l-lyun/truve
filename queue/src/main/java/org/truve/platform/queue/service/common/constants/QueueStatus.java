package org.truve.platform.queue.service.common.constants;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum QueueStatus {

	WAITING("대기중"),
	READY("티켓팅 진입 가능"),
	EXPIRE("티켓팅 진입 권한 만료")
	;

	private final String status;
}
