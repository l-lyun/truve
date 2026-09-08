package org.truve.platform.ticketing.service.warmup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.application.AvailabilityHealthContributorAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.truve.platform.ticketing.service.ticketing.constant.SeatStatus;
import org.truve.platform.ticketing.service.ticketing.dto.TicketingResponse;
import org.truve.platform.ticketing.service.ticketing.service.TicketingQueryService;

import com.truve.platform.common.config.ObjectMapperConfig;
import com.truve.platform.common.response.ApiResult;

import tools.jackson.databind.json.JsonMapper;

class TicketingWarmupHttpIntegrationTest {

	@Test
	@DisplayName("실제 HTTP 서버는 웜업 중 API를 차단하고 완료 후 같은 MVC mapper로 응답한다")
	void blocksTrafficUntilWarmupCompletes() throws Exception {
		try (RunningApplication app = new RunningApplication(false, "--ticketing.warmup.enabled=true",
			"--ticketing.warmup.show-schedule-id=1", "--ticketing.warmup.iterations=1")) {
			app.awaitBlockedRunner();
			ConfigurableApplicationContext context = app.context.get();
			TicketingWarmupRunner runner = context.getBean(TicketingWarmupRunner.class);
			assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.RUNNING);
			assertThat(app.get("/actuator/health/readiness").statusCode()).isEqualTo(503);
			assertThat(app.get("/actuator/health/liveness").statusCode()).isEqualTo(200);
			HttpResponse<String> blocked = app.get("/api/seats");
			assertThat(blocked.statusCode()).isEqualTo(503);
			assertThat(blocked.headers().firstValue("Retry-After")).contains("1");
			// Tomcat의 context path 제거, URL 디코딩과 경로 매개변수 정규화까지 거친 servletPath를 검증한다.
			assertThat(app.get("/%61pi/seats;probe=1").statusCode()).isEqualTo(503);
			assertThat(app.controllerCalls.get()).isZero();
			assertThat(app.readyEvents.get()).isZero();

			app.finishStartup();
			assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.COMPLETED);
			assertThat(app.get("/actuator/health/readiness").statusCode()).isEqualTo(200);
			assertThat(app.readyEvents.get()).isEqualTo(1);
			HttpResponse<String> response = app.get("/api/seats");
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(app.controllerCalls.get()).isEqualTo(1);

			JsonMapper mapper = context.getBean(JsonMapper.class);
			Object firstJsonConverter = context.getBean(RequestMappingHandlerAdapter.class)
				.getMessageConverters().stream()
				.filter(converter -> converter.canWrite(ApiResult.class, MediaType.APPLICATION_JSON))
				.findFirst().orElseThrow();
			assertThat(firstJsonConverter).isInstanceOf(JacksonJsonHttpMessageConverter.class);
			assertThat(((JacksonJsonHttpMessageConverter) firstJsonConverter).getMapper()).isSameAs(mapper);
			assertThat(ReflectionTestUtils.getField(runner, "jsonMapper")).isSameAs(mapper);
			assertThat(mapper.readTree(response.body()))
				.isEqualTo(mapper.readTree(mapper.writeValueAsString(ApiResult.ok(seats()))));
			assertThat(mapper.readTree(response.body()).at("/data/sections/0/rows/0/seats/0/status").asText())
				.isEqualTo("AVAILABLE");
		}
	}

	@Test
	@DisplayName("웜업 조회 실패는 기동을 실패시키며 준비 완료 이벤트를 발행하지 않는다")
	void failedWarmupDoesNotBecomeReady() throws Exception {
		try (RunningApplication app = new RunningApplication(true, "--ticketing.warmup.enabled=true",
			"--ticketing.warmup.show-schedule-id=1", "--ticketing.warmup.iterations=1")) {
			app.awaitBlockedRunner();
			TicketingWarmupRunner runner = app.context.get().getBean(TicketingWarmupRunner.class);
			assertThat(app.get("/actuator/health/readiness").statusCode()).isEqualTo(503);
			app.release.countDown();
			assertThatThrownBy(() -> app.startup.get(20, TimeUnit.SECONDS))
				.hasRootCauseMessage("조회 실패");
			assertThat(runner.snapshot().state()).isEqualTo(TicketingWarmupRunner.State.FAILED);
			assertThat(app.readyEvents.get()).isZero();
			assertThat(app.context.get().isActive()).isFalse();
			assertThat(app.controllerCalls.get()).isZero();
		}
	}

	@Test
	@DisplayName("기본 설정에서는 웜업과 gate bean 없이 기존 HTTP 응답을 유지한다")
	void disabledByDefault() throws Exception {
		try (RunningApplication app = new RunningApplication(false)) {
			app.finishStartup();
			ConfigurableApplicationContext context = app.context.get();
			assertThat(context.getBeansOfType(TicketingWarmupRunner.class)).isEmpty();
			assertThat(context.getBeansOfType(StartupTrafficGateFilter.class)).isEmpty();
			assertThat(app.get("/api/seats").statusCode()).isEqualTo(200);
			assertThat(app.get("/actuator/health/readiness").statusCode()).isEqualTo(200);
			verifyNoInteractions(context.getBean(TicketingQueryService.class));
		}
	}

	@Test
	@DisplayName("웜업 없는 비교군도 gate만 켜서 준비 완료 전 트래픽을 차단할 수 있다")
	void gateOnlyBaselineUsesApplicationReadiness() throws Exception {
		try (RunningApplication app = new RunningApplication(false, "--ticketing.startup.gate-enabled=true")) {
			app.awaitBlockedRunner();
			assertThat(app.context.get().getBeansOfType(TicketingWarmupRunner.class)).isEmpty();
			assertThat(app.get("/api/seats").statusCode()).isEqualTo(503);
			assertThat(app.get("/actuator/health/readiness").statusCode()).isEqualTo(503);
			assertThat(app.controllerCalls.get()).isZero();
			app.finishStartup();
			assertThat(app.get("/api/seats").statusCode()).isEqualTo(200);
			assertThat(app.get("/actuator/health/readiness").statusCode()).isEqualTo(200);
			verifyNoInteractions(app.context.get().getBean(TicketingQueryService.class));
		}
	}

	private static TicketingResponse.Seats seats() {
		return new TicketingResponse.Seats(List.of(new TicketingResponse.Section(1L, "VIP", "VIP", 150000L,
			List.of(new TicketingResponse.Row("A",
				List.of(new TicketingResponse.Seat(10L, 1L, SeatStatus.AVAILABLE)))))));
	}

	private static final class RunningApplication implements AutoCloseable {
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicInteger readyEvents = new AtomicInteger();
		private final AtomicInteger controllerCalls = new AtomicInteger();
		private final AtomicReference<ConfigurableApplicationContext> context = new AtomicReference<>();
		private final CompletableFuture<Integer> port = new CompletableFuture<>();
		private final ExecutorService executor = Executors.newSingleThreadExecutor();
		private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		private final boolean failQuery;
		private final Future<ConfigurableApplicationContext> startup;

		private RunningApplication(boolean failQuery, String... properties) {
			this.failQuery = failQuery;
			SpringApplication application = new SpringApplication(HttpTestConfiguration.class);
			application.setWebApplicationType(WebApplicationType.SERVLET);
			application.setRegisterShutdownHook(false);
			application.addInitializers(ctx -> {
				context.set(ctx);
				ctx.getBeanFactory().registerSingleton("runningApplication", this);
			});
			application.addListeners(event -> {
				if (event instanceof WebServerInitializedEvent serverEvent) {
					port.complete(serverEvent.getWebServer().getPort());
				}
				if (event instanceof ApplicationReadyEvent) {
					readyEvents.incrementAndGet();
				}
			});
			List<String> args = new ArrayList<>(List.of(
				"--spring.config.name=ticketing-warmup-http-test",
				"--server.port=0", "--server.servlet.context-path=/ticketing",
				"--management.endpoint.health.probes.enabled=true",
				"--management.endpoints.web.exposure.include=health",
				"--spring.main.banner-mode=off"));
			args.addAll(List.of(properties));
			startup = executor.submit(() -> application.run(args.toArray(String[]::new)));
		}

		private void blockRunner() throws InterruptedException {
			entered.countDown();
			if (!release.await(20, TimeUnit.SECONDS)) {
				throw new IllegalStateException("테스트 Runner 해제 시간 초과");
			}
		}

		private void awaitBlockedRunner() throws Exception {
			assertThat(entered.await(20, TimeUnit.SECONDS)).isTrue();
			port.get(5, TimeUnit.SECONDS);
		}

		private void finishStartup() throws Exception {
			release.countDown();
			startup.get(20, TimeUnit.SECONDS);
		}

		private HttpResponse<String> get(String path) throws Exception {
			URI uri = URI.create("http://127.0.0.1:" + port.get(5, TimeUnit.SECONDS) + "/ticketing" + path);
			return client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		}

		@Override
		public void close() throws Exception {
			release.countDown();
			try {
				try {
					startup.get(20, TimeUnit.SECONDS);
				} catch (java.util.concurrent.ExecutionException ignored) {
					// 실패 테스트에서는 SpringApplication이 이미 context를 닫는다.
				}
			} finally {
				if (context.get() != null) {
					context.get().close();
				}
				client.close();
				executor.shutdownNow();
			}
		}
	}

	@Configuration(proxyBeanMethods = false)
	@Import({TicketingWarmupRunner.class, StartupTrafficGateFilter.class, SeatsController.class,
		ObjectMapperConfig.class})
	@ImportAutoConfiguration({ConfigurationPropertiesAutoConfiguration.class, SslAutoConfiguration.class,
		ApplicationAvailabilityAutoConfiguration.class, TomcatServletWebServerAutoConfiguration.class,
		DispatcherServletAutoConfiguration.class, WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class,
		HttpMessageConvertersAutoConfiguration.class, EndpointAutoConfiguration.class,
		WebEndpointAutoConfiguration.class, HealthContributorRegistryAutoConfiguration.class,
		AvailabilityHealthContributorAutoConfiguration.class, HealthEndpointAutoConfiguration.class,
		AvailabilityProbesAutoConfiguration.class, ManagementContextAutoConfiguration.class})
	static class HttpTestConfiguration {
		@Bean
		TicketingQueryService ticketingQueryService(RunningApplication app) {
			TicketingQueryService service = mock(TicketingQueryService.class);
			when(service.getSeats(1L)).thenAnswer(invocation -> {
				app.blockRunner();
				if (app.failQuery) {
					throw new IllegalStateException("조회 실패");
				}
				return seats();
			});
			return service;
		}

		@Bean
		PlatformTransactionManager transactionManager() {
			PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
			when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
			return manager;
		}

		@Bean
		@ConditionalOnProperty(prefix = "ticketing.startup", name = "gate-enabled", havingValue = "true")
		ApplicationRunner baselineRunner(RunningApplication app) {
			return args -> app.blockRunner();
		}
	}

	@RestController
	static class SeatsController {
		private final RunningApplication app;

		SeatsController(RunningApplication app) {
			this.app = app;
		}

		@GetMapping("/api/seats")
		ApiResult<TicketingResponse.Seats> getSeats() {
			app.controllerCalls.incrementAndGet();
			return ApiResult.ok(seats());
		}
	}
}
