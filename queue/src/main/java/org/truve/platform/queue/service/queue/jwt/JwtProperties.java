package org.truve.platform.queue.service.queue.jwt;

import javax.crypto.SecretKey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@ConfigurationProperties(prefix = "jwt")
@Getter
public class JwtProperties {
	private final String secret;

	public SecretKey getSecret() {
		return Keys.hmacShaKeyFor(secret.getBytes());
	}
}
