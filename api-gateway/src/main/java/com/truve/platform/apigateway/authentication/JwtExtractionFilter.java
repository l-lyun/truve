package com.truve.platform.apigateway.authentication;

import javax.crypto.SecretKey;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class JwtExtractionFilter extends AbstractGatewayFilterFactory {

	private final JwtProperties jwtProperties;

	@Override
	public GatewayFilter apply(Object config) {
		return (exchange, chain) -> {
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

				return chain.filter(
					exchange.mutate()
						.request(
							exchange.getRequest()
								.mutate()
								.headers(headers -> {
									headers.remove("X-User-Id");
									headers.remove("X-User-Role");

									headers.set("X-User-Id", userId);
									headers.set("X-User-Role", role);
									headers.set("X-Token", accessToken);
								})
								.build()
						)
						.build()
				);
			} catch (Exception e) {
				return chain.filter(exchange);
			}
		};
	}
}
