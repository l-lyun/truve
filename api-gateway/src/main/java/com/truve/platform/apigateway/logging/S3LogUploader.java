package com.truve.platform.apigateway.logging;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3LogUploader {

	private static final DateTimeFormatter DATE_FORMATTER =
		DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT).withZone(ZoneOffset.UTC);

	@Value("${log.s3.bucket:}")
	private String bucket;

	@Value("${log.s3.region:ap-northeast-2}")
	private String region;

	@Value("${log.s3.endpoint:}")
	private String endpoint;

	@Value("${log.s3.access-key:}")
	private String accessKey;

	@Value("${log.s3.secret-key:}")
	private String secretKey;

	@Value("${log.s3.flush-size:100}")
	private int flushSize;

	@Value("${log.s3.flush-interval-seconds:5}")
	private int flushIntervalSeconds;

	private S3Client s3Client;
	private final Map<String, List<String>> buffers = new HashMap<>();
	private final ReentrantLock lock = new ReentrantLock();
	private ScheduledExecutorService flushScheduler;
	private ExecutorService uploadExecutor;

	@PostConstruct
	void init() {
		if (bucket.isBlank()) {
			return;
		}

		s3Client = buildS3Client();

		uploadExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "s3-log-uploader");
			t.setDaemon(true);
			return t;
		});

		flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "s3-log-flush-timer");
			t.setDaemon(true);
			return t;
		});
		flushScheduler.scheduleWithFixedDelay(
			this::triggerFlush,
			flushIntervalSeconds, flushIntervalSeconds, TimeUnit.SECONDS
		);
	}

	public void enqueue(String logJson, String s3Prefix) {
		if (s3Client == null)
			return;

		List<String> toUpload = null;
		lock.lock();
		try {
			List<String> buf = buffers.computeIfAbsent(s3Prefix, k -> new ArrayList<>());
			buf.add(logJson + "\n");
			if (buf.size() >= flushSize) {
				toUpload = drainPrefix(s3Prefix);
			}
		} finally {
			lock.unlock();
		}

		if (toUpload != null) {
			submitUpload(toUpload, s3Prefix);
		}
	}

	private void triggerFlush() {
		Map<String, List<String>> snapshot;
		lock.lock();
		try {
			snapshot = new HashMap<>();
			buffers.forEach((prefix, buf) -> {
				if (!buf.isEmpty()) {
					snapshot.put(prefix, new ArrayList<>(buf));
					buf.clear();
				}
			});
		} finally {
			lock.unlock();
		}
		snapshot.forEach((prefix, lines) -> submitUpload(lines, prefix));
	}

	private List<String> drainPrefix(String prefix) {
		List<String> buf = buffers.getOrDefault(prefix, new ArrayList<>());
		List<String> snapshot = new ArrayList<>(buf);
		buf.clear();
		return snapshot;
	}

	private void submitUpload(List<String> lines, String prefix) {
		uploadExecutor.submit(() -> upload(lines, prefix));
	}

	private void upload(List<String> lines, String prefix) {
		String content = String.join("", lines);
		Instant now = Instant.now();
		String key = prefix + "/" + DATE_FORMATTER.format(now) + "/" + now.toEpochMilli() + ".log";

		s3Client.putObject(
			PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType("application/json")
				.build(),
			RequestBody.fromString(content, StandardCharsets.UTF_8)
		);
	}

	private S3Client buildS3Client() {
		S3ClientBuilder builder = S3Client.builder().region(Region.of(region));

		if (!accessKey.isBlank() && !secretKey.isBlank()) {
			builder.credentialsProvider(StaticCredentialsProvider.create(
				AwsBasicCredentials.create(accessKey, secretKey)));
		} else {
			builder.credentialsProvider(DefaultCredentialsProvider.create());
		}

		if (!endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
		}

		return builder.build();
	}

	@PreDestroy
	void shutdown() {
		if (s3Client == null)
			return;
		if (flushScheduler != null)
			flushScheduler.shutdown();
		triggerFlush();
		if (uploadExecutor != null) {
			uploadExecutor.shutdown();
			try {
				uploadExecutor.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		s3Client.close();
	}
}
