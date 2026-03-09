package org.truve.platform.ticketing.service.global.jwt;

import org.springframework.stereotype.Component;
import org.truve.platform.ticketing.service.ticketing.dto.AdmissionTokenClaimsDTO;

import com.truve.platform.common.exception.CustomException;
import com.truve.platform.common.exception.ErrorCode;
import com.truve.platform.common.support.Preconditions;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdmissionTokenService {

	private static final String TOKEN_TYPE_KEY = "token_type";
	private static final String SHOW_ID_KEY = "show_id";
	private static final String ADMISSION_TOKEN_TYPE = "admission";

	private final JwtProperties jwtProperties;

	public AdmissionTokenClaimsDTO parseAdmissionToken(String token, Long expectedShowId, Long expectedUserId) {
		Preconditions.validate(!((token == null) || token.isEmpty()), ErrorCode.INVALID_ADMISSION_TOKEN);

		Claims claims = parseClaims(token);

		String tokenType = (String) claims.get(TOKEN_TYPE_KEY);
		Long showId = Long.parseLong((String) claims.get(SHOW_ID_KEY));
		Long userId = Long.parseLong(claims.getSubject());

		Preconditions.validate(ADMISSION_TOKEN_TYPE.equals(tokenType), ErrorCode.INVALID_ADMISSION_TOKEN);
		Preconditions.validate(userId.equals(expectedUserId), ErrorCode.INVALID_ADMISSION_TOKEN);
		Preconditions.validate(showId.equals(expectedShowId), ErrorCode.INVALID_ADMISSION_TOKEN);

		return AdmissionTokenClaimsDTO.of(userId, showId, tokenType);
	}

	private Claims parseClaims(String token) {
		try {
			return Jwts.parser()
				.verifyWith(jwtProperties.getSecret())
				.build()
				.parseSignedClaims(token)
				.getPayload();
		} catch (ExpiredJwtException e) {
			throw new CustomException(ErrorCode.EXPIRED_ADMISSION_TOKEN);
		} catch (JwtException | IllegalArgumentException e) {
			throw new CustomException(ErrorCode.INVALID_ADMISSION_TOKEN);
		}
	}
}
