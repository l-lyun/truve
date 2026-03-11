package com.truve.platform.musical.user.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.musical.user.domain.entity.User;
import com.truve.platform.musical.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserSignedUpEventConsumer {

	private static final String TOPIC =  "user.signedup.event";
	private static final String GROUP_ID = "musical-user-group";
	private static final String USER_ID = "userId";
	private static final String NICKNAME = "nickname";

	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;

	@KafkaListener(
		topics = TOPIC,
		groupId = GROUP_ID
	)
	@Transactional
	public void consume(String message) throws Exception {
		JsonNode root = objectMapper.readTree(message);

		Long userId = root.path(USER_ID).asLong();
		String nickname = root.path(NICKNAME).asText();

		if (userId == 0L || !StringUtils.hasText(nickname)) {
			throw new CustomException(ErrorCode.EVENT_USER_SIGNED_UP_FAILED);
		}

		if (userRepository.existsByUserId(userId)) {
			return;
		}

		userRepository.save(
			User.builder()
				.userId(userId)
				.nickname(nickname)
				.build()
		);
	}
}
