package org.truve.platform.ticketing.service.booking.outbox.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Local fault experiment only: pause after the claim transaction commits, before Kafka send. */
@Component
@Profile("outbox-fault-test")
@ConditionalOnProperty(name = "ticketing.outbox.fault.marker-file")
public class OutboxClaimFaultGate {
	private final Path markerFile;

	public OutboxClaimFaultGate(@Value("${ticketing.outbox.fault.marker-file}") String markerFile) {
		if (markerFile.isBlank() || !Path.of(markerFile).isAbsolute()) {
			throw new IllegalArgumentException("Fault marker must be an explicit absolute file path");
		}
		this.markerFile = Path.of(markerFile);
	}

	void afterClaimCommitted(List<ClaimedOutboxEvent> events) {
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			throw new IllegalStateException("Fault gate must run outside the claim transaction");
		}
		StringBuilder marker = new StringBuilder("id,claimToken\n");
		events.forEach(event -> marker.append(event.id()).append(',').append(event.claimToken()).append('\n'));
		try {
			Path temporary = Files.createTempFile(markerFile.getParent(), ".outbox-claim-", ".tmp");
			try {
				Files.writeString(temporary, marker);
				Files.move(temporary, markerFile, StandardCopyOption.ATOMIC_MOVE);
			} finally {
				Files.deleteIfExists(temporary);
			}
			new CountDownLatch(1).await();
		} catch (IOException exception) {
			throw new IllegalStateException("Cannot write Outbox fault marker", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Outbox fault pause interrupted; batch will not be sent", exception);
		}
	}
}
