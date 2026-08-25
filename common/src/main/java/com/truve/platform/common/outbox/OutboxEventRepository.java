package com.truve.platform.common.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface OutboxEventRepository<T extends OutboxEvent> extends JpaRepository<T, Long> {
	List<T> findByStatus(OutboxStatus status);
	List<T> findTop100ByStatusInOrderByIdAsc(List<OutboxStatus> statuses);

	void deleteByStatus(OutboxStatus status);
}
