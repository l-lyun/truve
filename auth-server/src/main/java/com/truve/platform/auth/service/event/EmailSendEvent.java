package com.truve.platform.auth.service.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmailSendEvent {
	private String email;
	private String subject;
	private String content;

	public static EmailSendEvent of(String email, String subject, String content) {
		return new EmailSendEvent(email, subject, content);
	}
}
