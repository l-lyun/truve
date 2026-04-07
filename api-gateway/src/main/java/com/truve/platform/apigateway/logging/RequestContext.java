package com.truve.platform.apigateway.logging;

import java.util.Map;

import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class RequestContext {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	long tsServer;
	String method;
	String path;
	String userId;
	String sessionTicket;
	Map<String, String> queryParams;
	JsonNode requestBody;
	Integer statusCode;

	public static RequestContext from(ServerWebExchange exchange, byte[] body) {
		return RequestContext.builder()
			.tsServer(System.currentTimeMillis())
			.method(exchange.getRequest().getMethod().name())
			.path(exchange.getRequest().getPath().value())
			.userId(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
			.sessionTicket(exchange.getRequest().getHeaders().getFirst("X-Session-Ticket"))
			.queryParams(exchange.getRequest().getQueryParams().toSingleValueMap())
			.requestBody(parseBody(body))
			.build();
	}

	public String toJson() {
		try {
			return MAPPER.writeValueAsString(this);
		} catch (Exception e) {
			return "{}";
		}
	}

	private static JsonNode parseBody(byte[] body) {
		try {
			if (body.length == 0) return MAPPER.createObjectNode();
			return MAPPER.readTree(body);
		} catch (Exception e) {
			return MAPPER.createObjectNode();
		}
	}
}
