package com.example.marketflow.MVCTHymeleafcontroller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.marketflow.exception.AuthenticationRequiredException;
import com.example.marketflow.marketplace.MarketplaceViews.PageView;
import com.example.marketflow.service.NotificationService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public String notifications(
            HttpSession session,
            Model model,
            @RequestParam(defaultValue = "0") int page
    ) {
        model.addAttribute(
                "notifications",
                PageView.of(notificationService.findForUser(actor(session), page, 20))
        );
        return "workspace/notifications";
    }

    @PostMapping("/{id}/read")
    public String markRead(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            HttpSession session
    ) {
        notificationService.markRead(actor(session), id);
        return "redirect:/notifications?page=" + Math.max(page, 0);
    }

    private Long actor(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new AuthenticationRequiredException();
        }
        return userId;
    }
}
