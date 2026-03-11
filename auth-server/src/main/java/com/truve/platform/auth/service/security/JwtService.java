package com.truve.platform.auth.service.security;

import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.truve.platform.common.constants.UserRole;
import com.truve.platform.auth.service.security.properties.JwtProperties;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtService {

	private static final String USER_PUBLIC_ID_CLAIM = "user_public_id";

	private final JwtProperties jwtProperties;

	public String issue(UUID userPublicId, Long userId, String email, UserRole role, Date expiration, String tokenType) {
		return Jwts.builder()
			.issuer("truve-api")
			.subject(email)
			.claim(USER_PUBLIC_ID_CLAIM, userPublicId.toString())
			.claim("user_id", userId)
			.claim("role", role.name())
			.claim("token_type", tokenType)
			.id(UUID.randomUUID().toString())
			.issuedAt(new Date())
			.expiration(expiration)
			.signWith(jwtProperties.getSecret())
			.compact();
	}

	public Date getAccessExpiration() {
		return jwtProperties.getAccessTokenExpiration();
	}

	public Date getRefreshExpiration() {
		return jwtProperties.getRefreshTokenExpiration();
	}

	public boolean validate(String token) {
		try {
			Jwts.parser()
				.verifyWith(jwtProperties.getSecret())
				.build()
				.parseSignedClaims(token);
			return true;
		} catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	public String parseEmail(String token) {
		return Jwts.parser()
			.verifyWith(jwtProperties.getSecret())
			.build()
			.parseSignedClaims(token)
			.getPayload()
			.getSubject();
	}

	public UUID parsePublicId(String token) {
		String publicId = Jwts.parser()
			.verifyWith(jwtProperties.getSecret())
			.build()
			.parseSignedClaims(token)
			.getPayload()
			.get(USER_PUBLIC_ID_CLAIM, String.class);

		return UUID.fromString(publicId);
	}

	// public Role parseRole(String token) {
	// 	String role = Jwts
	// 		.parser()
	// 		.verifyWith(jwtProperties.getSecret())
	// 		.build()
	// 		.parseSignedClaims(token)
	// 		.getPayload()
	// 		.get("role", String.class);
	//
	// 	return Role.valueOf(role);
	// }

	public String parseJti(String token) {
		return Jwts
			.parser()
			.verifyWith(jwtProperties.getSecret())
			.build()
			.parseSignedClaims(token)
			.getPayload()
			.getId();
	}

	public Date parseExpiration(String token) {
		return Jwts
			.parser()
			.verifyWith(jwtProperties.getSecret())
			.build()
			.parseSignedClaims(token)
			.getPayload()
			.getExpiration();
	}
}
