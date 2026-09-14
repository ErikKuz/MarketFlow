package com.example.marketflow.RestController;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.marketflow.exception.AuthenticationRequiredException;
import com.example.marketflow.marketplace.MarketplaceViews.PageView;
import com.example.marketflow.service.NotificationService;
import com.example.marketflow.service.NotificationService.NotificationView;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class RestNotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public PageView<NotificationView> notifications(
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageView.of(notificationService.findForUser(actor(session), page, size));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, HttpSession session) {
        notificationService.markRead(actor(session), id);
    }

    private Long actor(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new AuthenticationRequiredException();
        }
        return userId;
    }
}
