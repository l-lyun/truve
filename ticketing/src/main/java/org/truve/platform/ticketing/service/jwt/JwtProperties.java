package org.truve.platform.ticketing.service.jwt;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.jsonwebtoken.security.Keys;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
	private final String secret;
	public SecretKey getSecret() {
		return Keys.hmacShaKeyFor(secret.getBytes());
	}
}
