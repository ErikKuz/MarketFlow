package com.example.marketflow.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.NotificationRepository;
import com.example.marketflow.marketplace.MarketplaceAccess;
import com.example.marketflow.marketplace.MarketplaceException;
import com.example.marketflow.messaging.notification.NotificationEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<NotificationView> findForUser(Long userId, int page, int size) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                userId,
                MarketplaceAccess.page(page, size)
        ).map(NotificationView::of);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> MarketplaceException.missing("Уведомление не найдено"))
                .markRead();
    }

    public record NotificationView(
            Long id,
            UUID eventId,
            String recipientType,
            String title,
            String message,
            boolean read,
            Instant createdAt
    ) {
        static NotificationView of(NotificationEntity entity) {
            return new NotificationView(
                    entity.getId(), entity.getEventId(), entity.getRecipientType(),
                    entity.getTitle(), entity.getMessage(), entity.isRead(), entity.getCreatedAt()
            );
        }
    }
}
