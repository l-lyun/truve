package com.truve.platform.apigateway.logging;

import static net.logstash.logback.argument.StructuredArguments.*;

import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingFilter implements WebFilter, Ordered {

	private static final String TOPIC = "raw.gateway";
	private final KafkaTemplate<String, String> kafkaTemplate;

	private static final List<String> EXCLUDE_PATHS = List.of(
		"/api/auth",
		"/swagger-ui",
		"/v3/api-docs",
		"/api/auth/v3/api-docs",
		"/api/payments/v3/api-docs",
		"/api/queue/v3/api-docs",
		"/api/ticketing/v3/api-docs",
		"/api/musical/v3/api-docs"
	);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String path = exchange.getRequest().getPath().value();

		if (EXCLUDE_PATHS.stream().anyMatch(path::startsWith)) {
			return chain.filter(exchange);
		}

		return readBody(exchange)
			.flatMap(body -> {
				RequestContext ctx = RequestContext.from(exchange, body);
				return chain.filter(rebuildExchange(exchange, body))
					.then(Mono.fromRunnable(() -> {
						Integer status = exchange.getResponse().getStatusCode() != null
							? exchange.getResponse().getStatusCode().value()
							: null;
						String userId = exchange.getAttribute("userId");
						saveLog(ctx.toBuilder().statusCode(status).userId(userId).build());
					}));
			});
	}

	private Mono<byte[]> readBody(ServerWebExchange exchange) {
		return DataBufferUtils.join(exchange.getRequest().getBody())
			.defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
			.map(dataBuffer -> {
				byte[] bytes = new byte[dataBuffer.readableByteCount()];
				dataBuffer.read(bytes);
				DataBufferUtils.release(dataBuffer);
				return bytes;
			});
	}

	private ServerWebExchange rebuildExchange(ServerWebExchange exchange, byte[] body) {
		ServerHttpRequest mutatedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
			@Override
			public Flux<DataBuffer> getBody() {
				return Flux.just(exchange.getResponse().bufferFactory().wrap(body));
			}
		};
		return exchange.mutate().request(mutatedRequest).build();
	}

	private void saveLog(RequestContext ctx) {
		if (ctx.path.startsWith("/telemetry")) {
			log.info("",
				kv("type", "TELEMETRY"),
				kv("tsServer", ctx.tsServer),
				kv("userId", ctx.userId),
				kv("requestBody", ctx.requestBody)
			);
			//sendToKafka(ctx);
			return;
		}

		log.info("",
			kv("type", "REQUEST"),
			kv("tsServer", ctx.tsServer),
			kv("method", ctx.method),
			kv("path", ctx.path),
			kv("userId", ctx.userId),
			kv("sessionTicket", ctx.sessionTicket),
			kv("queryParams", ctx.queryParams),
			kv("statusCode", ctx.statusCode),
			kv("requestBody", ctx.requestBody)
		);
		//sendToKafka(ctx);
	}

	@Deprecated
	private void sendToKafka(RequestContext ctx) {
		kafkaTemplate.send(TOPIC, ctx.toJson());
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}
}
