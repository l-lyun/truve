package com.truve.platform.user.service.external;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuthClient {
	private final RestClient kakaoOauthRestClient;
}

