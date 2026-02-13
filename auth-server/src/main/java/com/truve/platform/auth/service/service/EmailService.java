package com.truve.platform.auth.service.service;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.common.support.VerificationCodeGenerateUtils;
import com.truve.platform.auth.service.domain.entity.EmailVerificationToken;
import com.truve.platform.auth.service.repository.EmailVerificationRepository;
import com.truve.platform.auth.service.repository.UserRepository;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

	private final UserRepository userRepository;
	private final EmailVerificationRepository emailVerificationRepository;
	private final JavaMailSender mailSender;
	private final VerificationCodeGenerateUtils verificationCodeGenerateUtils;

	@Value("${spring.mail.username}")
	private String senderEmail;

	@Transactional
	public void sendMail(String email) {
		Preconditions.validate(!userRepository.existsByEmail(email), ErrorCode.ALREADY_EXISTS_EMAIL);

		String code = verificationCodeGenerateUtils.generateVerificationCode();

		EmailVerificationToken emailVerificationToken = EmailVerificationToken.create(
			email,
			code
		);

		emailVerificationRepository.save(emailVerificationToken);

		String subject = "TRUVE 회원가입 인증 코드";
		String text = String.format(
			"<p>안녕하세요!</p>" +
				"<p>TRUVE 회원가입을 위한 인증 코드입니다:</p>" +
				"<h2 style='color:blue;'>%s</h2>" +
				"<p>이 코드는 <b>10분 동안 유효</b>합니다.</p>" +
				"<p>감사합니다.</p>", code
		);

		try {
			MimeMessage message = mailSender.createMimeMessage();
			message.setFrom(senderEmail);
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

			helper.setFrom(senderEmail);
			helper.setTo(email);
			helper.setSubject(subject);
			helper.setText(text, true);

			mailSender.send(message);
		} catch (MessagingException e) {
			log.error("이메일 전송 실패 - 받는 사람: {}, 제목: {}", email, subject, e);
			throw new CustomException(ErrorCode.NOT_FOUND_EMAIL);
		}
	}

	@Transactional
	public void verifyEmail(String email, String code) {

		EmailVerificationToken token = emailVerificationRepository.findByEmailOrThrow(email);

		Preconditions.validate(!token.isVerified(), ErrorCode.ALREADY_EXISTS_EMAIL);

		Preconditions.validate(token.getCode().equals(code), ErrorCode.NOT_CORRECT_EMAIL_CODE);

		token.verifyEmail();
	}
}
