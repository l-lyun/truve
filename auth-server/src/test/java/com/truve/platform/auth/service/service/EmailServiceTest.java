package com.truve.platform.auth.service.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import com.truve.platform.auth.service.repository.EmailVerificationRepository;
import com.truve.platform.auth.service.repository.UserRepository;
import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.VerificationCodeGenerateUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private EmailVerificationRepository emailVerificationRepository;
	@Mock
	private JavaMailSender mailSender;
	@Mock
	private VerificationCodeGenerateUtils verificationCodeGenerateUtils;

	@InjectMocks
	private EmailService emailService;

	@Nested
	@DisplayName("인증 메일 전송 테스트")
	class SendMailTest {

		@Test
		@DisplayName("중복되지 않은 이메일이면 인증 코드를 저장하고 메일을 전송한다.")
		void 메일_전송_성공() {
			// given
			String email = "new@test.com";
			String code = "123456";
			MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

			ReflectionTestUtils.setField(emailService, "senderEmail", "noreply@truve.com");
			given(userRepository.existsByEmail(email)).willReturn(false);
			given(verificationCodeGenerateUtils.generateVerificationCode()).willReturn(code);
			given(mailSender.createMimeMessage()).willReturn(mimeMessage);

			// when
			emailService.sendMail(email);

			// then
			verify(emailVerificationRepository).registerEmailVerificationCode(email, code);
			verify(mailSender).send(mimeMessage);
		}

		@Test
		@DisplayName("이미 가입된 이메일이면 예외가 발생한다.")
		void 메일_전송_실패_중복_이메일() {
			// given
			String email = "dup@test.com";
			given(userRepository.existsByEmail(email)).willReturn(true);

			// when
			CustomException exception = assertThrows(CustomException.class, () -> emailService.sendMail(email));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ALREADY_EXISTS_EMAIL);
		}

	}

	@Nested
	@DisplayName("이메일 인증 테스트")
	class VerifyEmailTest {

		@Test
		@DisplayName("코드가 일치하면 인증 완료 처리를 수행한다.")
		void 이메일_인증_성공() {
			// given
			String email = "new@test.com";
			String code = "123456";
			given(emailVerificationRepository.verifyEmailVerificationCode(email, code)).willReturn(true);

			// when
			emailService.verifyEmail(email, code);

			// then
			verify(emailVerificationRepository).registerVerifiedEmail(email);
			verify(emailVerificationRepository).deleteEmailVerificationCode(email);
		}

		@Test
		@DisplayName("코드가 일치하지 않으면 예외가 발생한다.")
		void 이메일_인증_실패_코드_불일치() {
			// given
			String email = "new@test.com";
			String code = "wrong";
			given(emailVerificationRepository.verifyEmailVerificationCode(email, code)).willReturn(false);

			// when
			CustomException exception = assertThrows(CustomException.class, () -> emailService.verifyEmail(email, code));

			// then
			assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_CORRECT_EMAIL_CODE);
			verify(emailVerificationRepository, never()).registerVerifiedEmail(anyString());
			verify(emailVerificationRepository, never()).deleteEmailVerificationCode(anyString());
		}
	}
}
