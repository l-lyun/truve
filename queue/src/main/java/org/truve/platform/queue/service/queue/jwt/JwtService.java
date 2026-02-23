package org.truve.platform.queue.service.queue.jwt;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;

@Component
public class JwtService {

	private final SecretKey secret;

	public JwtService(JwtProperties jwtProperties) {
		this.secret = jwtProperties.getSecret();
	}

	public String issue(String showId, String userId, long ttlSec) {

		// TODO: jti로 검증할 지 논의 후 결정
		String jti = UUID.randomUUID().toString();
		Date now = new Date();
		Date expiration =  new Date(now.getTime() + ttlSec * 1000);

		return Jwts.builder()
			.subject(userId)
			.claim("show_id", showId)
			.claim("token_type", "admission")
			.id(jti)
			.issuedAt(now)
			.expiration(expiration)
			.signWith(secret)
			.compact();
	}
}
