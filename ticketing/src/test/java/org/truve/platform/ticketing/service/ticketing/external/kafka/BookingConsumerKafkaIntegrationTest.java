package org.truve.platform.ticketing.service.ticketing.external.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import org.truve.platform.ticketing.service.booking.external.kafka.TicketingEventCommand;
import org.truve.platform.ticketing.service.ticketing.config.BookingConsumerKafkaConfig;
import org.truve.platform.ticketing.service.ticketing.config.BookingConsumerKafkaProperties;
import org.truve.platform.ticketing.service.ticketing.service.HoldRequestedEventHandler;
import org.truve.platform.ticketing.service.ticketing.service.ScheduledSeatStatusService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.truve.platform.common.support.JsonConverter;

@SpringJUnitConfig(BookingConsumerKafkaIntegrationTest.KafkaTestConfiguration.class)
@Testcontainers
class BookingConsumerKafkaIntegrationTest {
	private static final String DLT_TOPIC = "booking.ticketing.dlt";
	private static final String TRANSIENT_KEY = "retry-success";
	private static final String PERSISTENT_KEY = "retry-exhausted";
	private static final String INVALID_JSON_KEY = "invalid-json";
	private static final String UNKNOWN_TYPE_KEY = "unknown-type";
	private static final String DLT_FAILURE_KEY = "dlt-publish-failure";
	private static final DeliveryProbe DELIVERY_PROBE = new DeliveryProbe();

	@Container
	private static final KafkaContainer KAFKA = new KafkaContainer(
		DockerImageName.parse("apache/kafka:4.0.0"));

	@DynamicPropertySource
	static void kafkaProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
		registry.add("spring.kafka.producer.key-serializer", () -> StringSerializer.class.getName());
		registry.add("spring.kafka.producer.value-serializer", () -> StringSerializer.class.getName());
		registry.add("spring.kafka.consumer.key-deserializer", () -> StringDeserializer.class.getName());
		registry.add("spring.kafka.consumer.value-deserializer", () -> StringDeserializer.class.getName());
		registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
		registry.add("ticketing.kafka.booking-consumer.dlt-topic", () -> DLT_TOPIC);
		registry.add("ticketing.kafka.booking-consumer.retry-interval", () -> "50ms");
		// FixedBackOff의 값은 최초 호출을 제외한 재시도 횟수다: 최초 1회 + 재시도 2회.
		registry.add("ticketing.kafka.booking-consumer.max-attempts", () -> "2");
	}

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private BookingConsumerKafkaProperties properties;
	@Autowired
	private KafkaListenerEndpointRegistry listenerRegistry;
	@MockitoBean
	private HoldRequestedEventHandler holdRequestedEventHandler;
	@MockitoBean
	private ScheduledSeatStatusService scheduledSeatStatusService;

	@Test
	void 실제_Kafka에서_재시도_DLT와_DLT발행실패_offset미커밋을_검증한다() throws Exception {
		AtomicInteger transientAttempts = new AtomicInteger();
		AtomicInteger persistentAttempts = new AtomicInteger();
		AtomicInteger dltFailureAttempts = new AtomicInteger();
		willAnswer(invocation -> {
			TicketingEventCommand.HoldRequested event = invocation.getArgument(0);
			if (event.getHoldId().equals("H-TRANSIENT") && transientAttempts.incrementAndGet() < 3) {
				throw new IllegalStateException("transient failure");
			}
			if (event.getHoldId().equals("H-PERSISTENT")) {
				persistentAttempts.incrementAndGet();
				throw new IllegalStateException("persistent failure");
			}
			if (event.getHoldId().equals("H-DLT-FAIL")) {
				dltFailureAttempts.incrementAndGet();
				throw new IllegalStateException("persistent failure");
			}
			return null;
		}).given(holdRequestedEventHandler).handle(any(TicketingEventCommand.HoldRequested.class));

		long committedBefore;
		try (Consumer<String, String> dltConsumer = createDltConsumer("dlt-verifier")) {
			dltConsumer.subscribe(List.of(DLT_TOPIC));

			send(TRANSIENT_KEY, validPayload("H-TRANSIENT", "R-TRANSIENT"), "HOLD_REQUESTED");
			verify(holdRequestedEventHandler, timeout(10_000).times(3))
				.handle(argThat(event -> event.getHoldId().equals("H-TRANSIENT")));

			send(PERSISTENT_KEY, validPayload("H-PERSISTENT", "R-PERSISTENT"), "HOLD_REQUESTED");
			send(INVALID_JSON_KEY, "{invalid-json", "HOLD_REQUESTED");
			send(UNKNOWN_TYPE_KEY, "{}", "UNKNOWN");

			verify(holdRequestedEventHandler, timeout(10_000).times(3))
				.handle(argThat(event -> event.getHoldId().equals("H-PERSISTENT")));

			List<ConsumerRecord<String, String>> dltRecords = waitForRecords(dltConsumer, 3, Duration.ofSeconds(15));
			KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(1)).forEach(dltRecords::add);
			assertThat(dltRecords).extracting(ConsumerRecord::key)
				.containsExactlyInAnyOrder(PERSISTENT_KEY, INVALID_JSON_KEY, UNKNOWN_TYPE_KEY)
				.doesNotContain(TRANSIENT_KEY);
			assertThat(recordFor(dltRecords, PERSISTENT_KEY).value())
				.isEqualTo(validPayload("H-PERSISTENT", "R-PERSISTENT"));
			assertThat(headerValue(recordFor(dltRecords, PERSISTENT_KEY), "event-type"))
				.isEqualTo("HOLD_REQUESTED");
			assertThat(transientAttempts).hasValue(3);
			assertThat(persistentAttempts).hasValue(3);
			assertThat(DELIVERY_PROBE.count(TRANSIENT_KEY)).isEqualTo(3);
			assertThat(DELIVERY_PROBE.count(PERSISTENT_KEY)).isEqualTo(3);
			assertThat(DELIVERY_PROBE.count(INVALID_JSON_KEY)).isEqualTo(1);
			assertThat(DELIVERY_PROBE.count(UNKNOWN_TYPE_KEY)).isEqualTo(1);
			committedBefore = waitForCommittedOffset(offset -> offset >= 4L, Duration.ofSeconds(10));
		}

		properties.setDltTopic("invalid topic");

		try {
			send(DLT_FAILURE_KEY, validPayload("H-DLT-FAIL", "R-DLT-FAIL"), "HOLD_REQUESTED");
			verify(holdRequestedEventHandler, timeout(10_000).atLeast(4))
				.handle(argThat(event -> event.getHoldId().equals("H-DLT-FAIL")));
		} finally {
			listenerRegistry.stop();
		}

		assertThat(dltFailureAttempts.get()).isGreaterThanOrEqualTo(4);
		assertThat(DELIVERY_PROBE.count(DLT_FAILURE_KEY)).isGreaterThanOrEqualTo(4);
		assertThat(currentCommittedOffset()).isEqualTo(committedBefore);
	}

	private String validPayload(String holdId, String reservationNumber) throws Exception {
		return objectMapper.writeValueAsString(TicketingEventCommand.HoldRequested.of(
			holdId,
			reservationNumber,
			UUID.fromString("11111111-1111-1111-1111-111111111111"),
			"session-token",
			1L,
			List.of(10L, 11L),
			LocalDateTime.of(2026, 8, 28, 18, 0)
		));
	}

	private void send(String key, String payload, String eventType) throws Exception {
		ProducerRecord<String, String> record = new ProducerRecord<>(BookingConsumer.TOPIC, key, payload);
		record.headers().add("event-type", eventType.getBytes(UTF_8));
		kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
	}

	private Consumer<String, String> createDltConsumer(String group) {
		Map<String, Object> consumerProperties = KafkaTestUtils.consumerProps(
			KAFKA.getBootstrapServers(), group + "-" + UUID.randomUUID(), false);
		consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		return new DefaultKafkaConsumerFactory<>(
			consumerProperties,
			new StringDeserializer(),
			new StringDeserializer()
		).createConsumer();
	}

	private List<ConsumerRecord<String, String>> waitForRecords(
		Consumer<String, String> consumer,
		int expectedCount,
		Duration timeout
	) {
		long deadline = System.nanoTime() + timeout.toNanos();
		List<ConsumerRecord<String, String>> records = new ArrayList<>();
		while (records.size() < expectedCount && System.nanoTime() < deadline) {
			ConsumerRecords<String, String> polled = KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500));
			polled.forEach(records::add);
		}
		return records;
	}

	private ConsumerRecord<String, String> recordFor(
		List<ConsumerRecord<String, String>> records,
		String key
	) {
		return records.stream().filter(record -> key.equals(record.key())).findFirst().orElseThrow();
	}

	private String headerValue(ConsumerRecord<String, String> record, String name) {
		Header header = record.headers().lastHeader(name);
		return header == null ? null : new String(header.value(), UTF_8);
	}

	private long waitForCommittedOffset(Predicate<Long> condition, Duration timeout) throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		long offset = currentCommittedOffset();
		while (!condition.test(offset) && System.nanoTime() < deadline) {
			Thread.sleep(50);
			offset = currentCommittedOffset();
		}
		assertThat(condition.test(offset)).isTrue();
		return offset;
	}

	private long currentCommittedOffset() throws Exception {
		try (AdminClient admin = AdminClient.create(Map.of(
			AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
			Map<TopicPartition, OffsetAndMetadata> offsets = admin
				.listConsumerGroupOffsets(BookingConsumer.GROUP)
				.partitionsToOffsetAndMetadata()
				.get(10, TimeUnit.SECONDS);
			OffsetAndMetadata offset = offsets.get(new TopicPartition(BookingConsumer.TOPIC, 0));
			return offset == null ? -1L : offset.offset();
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableKafka
	@EnableConfigurationProperties(BookingConsumerKafkaProperties.class)
	@ImportAutoConfiguration(KafkaAutoConfiguration.class)
	@Import({
		BookingConsumerKafkaConfig.class,
		BookingConsumer.class,
		JsonConverter.class
	})
	static class KafkaTestConfiguration {
		@Bean
		static BeanPostProcessor deliveryProbePostProcessor() {
			return new BeanPostProcessor() {
				@Override
				@SuppressWarnings("unchecked")
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (BookingConsumerKafkaConfig.CONTAINER_FACTORY.equals(beanName)
						&& bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
						ConcurrentKafkaListenerContainerFactory<Object, Object> typedFactory =
							(ConcurrentKafkaListenerContainerFactory<Object, Object>)factory;
						typedFactory.setRecordInterceptor((record, consumer) -> {
							DELIVERY_PROBE.record(record.key());
							return record;
						});
					}
					return bean;
				}
			};
		}

		@Bean
		ObjectMapper objectMapper() {
			return new ObjectMapper().findAndRegisterModules();
		}
	}

	private static final class DeliveryProbe {
		private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

		void record(Object key) {
			attempts.computeIfAbsent(String.valueOf(key), ignored -> new AtomicInteger()).incrementAndGet();
		}

		int count(String key) {
			return attempts.getOrDefault(key, new AtomicInteger()).get();
		}
	}
}
