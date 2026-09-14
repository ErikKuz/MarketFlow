package com.example.marketflow.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.marketflow.messaging.outbox.OutboxEventEntity;
import com.example.marketflow.messaging.outbox.OutboxStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAscIdAsc(
            OutboxStatus status,
            Instant availableAt,
            Pageable pageable
    );

    Optional<OutboxEventEntity> findByEventId(UUID eventId);
}
