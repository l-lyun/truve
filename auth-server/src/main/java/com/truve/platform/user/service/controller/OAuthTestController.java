package com.truve.platform.user.service.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// OAuth 테스트 컨트롤러입니다. 프론트와 연동 이전 뷰 대응 용도입니다.
@Controller
@RequestMapping("/test")
public class OAuthTestController {

	@GetMapping("/login")
	public String loginPage() {
		return "login"; // templates/login.html
	}

	@GetMapping("/callback")
	public String callbackPage() {
		return "callback"; // templates/callback.html
	}

}
