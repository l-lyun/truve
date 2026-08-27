package org.truve.platform.ticketing.service.booking.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class NumberGeneratorTest {
	private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

	@Test
	void 같은_멱등키는_요청_좌석과_무관하게_같은_holdId를_생성한다() {
		String first = NumberGenerator.generateHoldId(USER_ID, 100L, "request-001");
		String retried = NumberGenerator.generateHoldId(USER_ID, 100L, "request-001");

		assertThat(retried).isEqualTo(first);
	}

	@Test
	void 멱등키가_다르면_다른_holdId를_생성한다() {
		String original = NumberGenerator.generateHoldId(USER_ID, 100L, "request-001");

		assertThat(NumberGenerator.generateHoldId(USER_ID, 100L, "request-002"))
			.isNotEqualTo(original);
	}

	@Test
	void 좌석_fingerprint는_순서와_무관하고_좌석이_다르면_구분된다() {
		String first = NumberGenerator.generateHoldRequestFingerprint(List.of(11L, 10L));

		assertThat(NumberGenerator.generateHoldRequestFingerprint(List.of(10L, 11L))).isEqualTo(first);
		assertThat(NumberGenerator.generateHoldRequestFingerprint(List.of(10L, 12L))).isNotEqualTo(first);
	}
}
