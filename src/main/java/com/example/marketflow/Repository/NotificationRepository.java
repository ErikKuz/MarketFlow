package com.example.marketflow.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.marketflow.messaging.notification.NotificationEntity;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    boolean existsByEventIdAndUserIdAndRecipientType(UUID eventId, Long userId, String recipientType);

    Page<NotificationEntity> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndUserId(Long id, Long userId);
}
