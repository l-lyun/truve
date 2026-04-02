package com.truve.platform.auth.service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.truve.platform.auth.service.event.EmailSendEvent;
import com.truve.platform.auth.service.event.EmailEventService;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;
import com.truve.platform.common.support.VerificationCodeGenerateUtils;
import com.truve.platform.auth.service.repository.EmailVerificationRepository;
import com.truve.platform.auth.service.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

	private final UserRepository userRepository;
	private final EmailVerificationRepository emailVerificationRepository;
	private final VerificationCodeGenerateUtils verificationCodeGenerateUtils;
	private final EmailEventService emailEventService;

	@Transactional
	public void sendMail(String email) {
		Preconditions.validate(!userRepository.existsByEmail(email), ErrorCode.ALREADY_EXISTS_EMAIL);

		String code = verificationCodeGenerateUtils.generateVerificationCode();
		emailVerificationRepository.registerEmailVerificationCode(email, code);

		String subject = "TRUVE 회원가입 인증 코드";
		String text = String.format(
			"<p>안녕하세요!</p>" +
				"<p>TRUVE 회원가입을 위한 인증 코드입니다:</p>" +
				"<h2 style='color:blue;'>%s</h2>" +
				"<p>이 코드는 <b>10분 동안 유효</b>합니다.</p>" +
				"<p>감사합니다.</p>", code
		);

		emailEventService.sendEmail(EmailSendEvent.of(email, subject, text));
	}

	@Transactional
	public void verifyEmail(String email, String code) {
		boolean isVerified = emailVerificationRepository.verifyEmailVerificationCode(email, code);
		Preconditions.validate(isVerified, ErrorCode.NOT_CORRECT_EMAIL_CODE);
		emailVerificationRepository.registerVerifiedEmail(email);
		emailVerificationRepository.deleteEmailVerificationCode(email);
	}
}
