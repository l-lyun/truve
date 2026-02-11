package com.truve.platform.user.service.service;

import org.springframework.stereotype.Service;

import com.truve.platform.user.service.security.properties.KakaoOAuthProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KakaoOAuthService {
	public final KakaoOAuthProperties kakaoOAuthProperties;

	public void login(String code, String error, String errorDescription, String state) {

	}
}
