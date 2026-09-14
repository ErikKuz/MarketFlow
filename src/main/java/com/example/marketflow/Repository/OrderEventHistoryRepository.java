package com.example.marketflow.Repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.marketflow.messaging.history.OrderEventHistoryEntity;

public interface OrderEventHistoryRepository extends JpaRepository<OrderEventHistoryEntity, Long> {
    boolean existsByEventId(UUID eventId);

    List<OrderEventHistoryEntity> findAllByOrderIdOrderByOccurredAtAscIdAsc(Long orderId);
}
