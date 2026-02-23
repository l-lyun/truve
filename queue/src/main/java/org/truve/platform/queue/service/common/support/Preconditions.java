package org.truve.platform.queue.service.common.support;

import org.truve.platform.queue.service.common.exception.CustomException;
import org.truve.platform.queue.service.common.exception.ErrorCode;

public class Preconditions {

	public static void validate(boolean expression, ErrorCode errorCode) {
		if (!expression) {
			throw new CustomException(errorCode);
		}
	}
}
