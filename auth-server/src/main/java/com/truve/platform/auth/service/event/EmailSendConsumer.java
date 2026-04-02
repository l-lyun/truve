package com.truve.platform.auth.service.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.JsonConverter;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSendConsumer {

	private final JavaMailSender mailSender;
	private final JsonConverter jsonConverter;

	@KafkaListener(
		topics = "email.send",
		groupId = "email-send-group"
	)
	public void consume(String message) {
		EmailSendEvent event = jsonConverter.convert(message, EmailSendEvent.class);

		try {
			MimeMessage mimeMessage = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

			helper.setTo(event.getEmail());
			helper.setSubject(event.getSubject());
			helper.setText(event.getContent(), true);

			mailSender.send(mimeMessage);
			log.info("이메일 발송 완료 - to={}", event.getEmail());
		} catch (MessagingException e) {
			log.error("이메일 발송 실패 - to={}", event.getEmail(), e);
			throw new CustomException(ErrorCode.NOT_FOUND_EMAIL);
		}
	}
}
