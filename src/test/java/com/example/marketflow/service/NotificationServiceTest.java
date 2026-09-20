package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.marketflow.Repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void countsOnlyUnreadNotificationsOfRequestedUser() {
        when(notificationRepository.countByUserIdAndReadFalse(7L)).thenReturn(4L);

        long result = notificationService.countUnread(7L);

        assertEquals(4L, result);
        verify(notificationRepository).countByUserIdAndReadFalse(7L);
    }
}
