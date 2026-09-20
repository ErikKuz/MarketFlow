package com.example.marketflow.RestController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.service.NotificationService;
import com.example.marketflow.service.NotificationService.NotificationView;

@WebMvcTest(RestNotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RestNotificationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void returnsOnlyCurrentUserNotifications() throws Exception {
        MockHttpSession session = session(7L);
        NotificationView notification = new NotificationView(
                1L, UUID.randomUUID(), "BUYER", "Заказ оплачен",
                "Заказ оплачен. Заказ №42", false, Instant.parse("2026-01-01T00:00:00Z")
        );
        when(notificationService.findForUser(7L, 0, 20)).thenReturn(
                new PageImpl<>(List.of(notification), PageRequest.of(0, 20), 1)
        );

        mockMvc.perform(get("/api/v1/notifications").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Заказ оплачен"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void marksOwnNotificationRead() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/5/read").session(session(7L)))
                .andExpect(status().isNoContent());
        verify(notificationService).markRead(7L, 5L);
    }

    @Test
    void returnsUnreadCountForCurrentUser() throws Exception {
        when(notificationService.countUnread(7L)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));

        verify(notificationService).countUnread(7L);
    }

    @Test
    void requiresSession() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    private MockHttpSession session(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);
        return session;
    }
}
