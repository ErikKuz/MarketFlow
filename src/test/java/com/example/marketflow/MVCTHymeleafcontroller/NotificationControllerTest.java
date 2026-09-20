package com.example.marketflow.MVCTHymeleafcontroller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.service.NotificationService;
import com.example.marketflow.service.NotificationService.NotificationView;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(UiLabels.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void showsCurrentUserNotificationsPage() throws Exception {
        NotificationView notification = new NotificationView(
                1L,
                UUID.fromString("8b679b54-37f9-4d9a-8f5f-50e3559ed38f"),
                "BUYER",
                "Заказ оплачен",
                "Заказ №42 успешно оплачен",
                false,
                Instant.parse("2026-09-14T10:05:00Z")
        );
        when(notificationService.findForUser(7L, 1, 20)).thenReturn(
                new PageImpl<>(List.of(notification), PageRequest.of(1, 20), 21)
        );

        mockMvc.perform(get("/notifications")
                        .session(session(7L))
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/notifications"))
                .andExpect(model().attributeExists("notifications"));

        verify(notificationService).findForUser(7L, 1, 20);
    }

    @Test
    void marksNotificationReadAndReturnsToRequestedPage() throws Exception {
        mockMvc.perform(post("/notifications/{id}/read", 5L)
                        .session(session(7L))
                        .param("page", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications?page=2"));

        verify(notificationService).markRead(7L, 5L);
    }

    private MockHttpSession session(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);
        return session;
    }
}
