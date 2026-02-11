package com.truve.platform.apigateway.authentication;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory {

	private final AccessTokenBlacklistService blacklistService;
	private final JwtProperties jwtProperties;

	@Override
	public GatewayFilter apply(Object config) {
		return (exchange, chain) -> {
			String token = exchange.getRequest()
				.getHeaders()
				.getFirst("Authorization");

			if (token == null || !token.startsWith("Bearer ")) {
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return exchange.getResponse().setComplete();
			}

			token = token.substring(7);

			SecretKey secretKey = jwtProperties.getSecretKey();
			Claims claims = Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

			String jti = claims.getId();
			if (jti != null && blacklistService.isExist(jti)) {
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return exchange.getResponse().setComplete();
			}

			Long userId = claims.get("user_id", Long.class);
			String role = claims.get("role", String.class);
			String tokenType = claims.get("token_type", String.class);

			if (!"access".equals(tokenType)) {
				exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
				return exchange.getResponse().setComplete();
			}

			return chain.filter(
				exchange.mutate()
					.request(
						exchange.getRequest()
							.mutate()
							.header("X-User-Id", userId.toString())
							.header("X-User-Role", role)
							.header("X-Token", token)
							.build()
					)
					.build()
			);
		};
	}

}
