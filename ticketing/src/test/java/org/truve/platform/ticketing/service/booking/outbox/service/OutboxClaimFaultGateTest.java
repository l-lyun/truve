package org.truve.platform.ticketing.service.booking.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OutboxClaimFaultGateTest {
	@TempDir
	Path directory;

	@Test
	void 프로필과_명시적_경로가_함께_있어야_활성화된다() {
		for (boolean profile : List.of(false, true)) {
			for (boolean property : List.of(false, true)) {
				try (var context = new AnnotationConfigApplicationContext()) {
					if (profile) context.getEnvironment().setActiveProfiles("outbox-fault-test");
					if (property) context.getEnvironment().getPropertySources().addFirst(
						new MapPropertySource("test", Map.of("ticketing.outbox.fault.marker-file", directory.resolve("marker.csv").toString())));
					context.register(OutboxClaimFaultGate.class);
					context.refresh();
					assertThat(context.getBeansOfType(OutboxClaimFaultGate.class)).hasSize(profile && property ? 1 : 0);
				}
			}
		}
	}

	@Test
	void 커밋된_claim_표식을_남기고_인터럽트까지_대기한다() throws Exception {
		Path marker = directory.resolve("claimed.csv");
		UUID token = UUID.randomUUID();
		var event = new ClaimedOutboxEvent(7L, "experiment", "key", "{}", "TEST", token);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread worker = new Thread(() -> {
			try {
				new OutboxClaimFaultGate(marker.toString()).afterClaimCommitted(List.of(event));
			} catch (Throwable exception) {
				failure.set(exception);
			}
		});
		worker.start();
		try {
			long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
			while (!Files.exists(marker) && worker.isAlive() && System.nanoTime() < deadline) Thread.sleep(10);
			assertThat(Files.readString(marker)).isEqualTo("id,claimToken\n7," + token + "\n");
			assertThat(worker.isAlive()).isTrue();
		} finally {
			worker.interrupt();
			worker.join(3000);
		}
		assertThat(worker.isAlive()).isFalse();
		assertThat(failure.get()).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
	}

	@Test
	void 트랜잭션_중에는_표식을_남기지_않는다() {
		Path marker = directory.resolve("forbidden.csv");
		TransactionSynchronizationManager.setActualTransactionActive(true);
		try {
			assertThatThrownBy(() -> new OutboxClaimFaultGate(marker.toString()).afterClaimCommitted(List.of()))
				.isInstanceOf(IllegalStateException.class);
			assertThat(marker).doesNotExist();
		} finally {
			TransactionSynchronizationManager.clear();
		}
	}
}
