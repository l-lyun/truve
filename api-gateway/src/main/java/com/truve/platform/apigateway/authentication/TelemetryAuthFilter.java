package com.truve.platform.apigateway.authentication;

import javax.crypto.SecretKey;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TelemetryAuthFilter implements WebFilter, Ordered {

	private final JwtProperties jwtProperties;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		if (!exchange.getRequest().getPath().value().startsWith("/telemetry")) {
			return chain.filter(exchange);
		}

		String token = exchange.getRequest()
			.getHeaders()
			.getFirst("Authorization");

		if (token == null || !token.startsWith("Bearer ")) {
			return chain.filter(exchange);
		}

		try {
			String accessToken = token.substring(7);
			SecretKey secretKey = jwtProperties.getSecretKey();
			Claims claims = Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(accessToken)
				.getPayload();

			String userId = claims.get("user_public_id", String.class);
			String role = claims.get("role", String.class);
			String tokenType = claims.get("token_type", String.class);

			if (!"access".equals(tokenType) || !StringUtils.hasText(tokenType)) {
				return chain.filter(exchange);
			}

			exchange.getAttributes().put("userId", userId);
			return chain.filter(exchange);
		} catch (JwtException e) {
			return chain.filter(exchange);
		}
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 1;
	}
}
