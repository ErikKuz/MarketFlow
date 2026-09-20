package com.example.marketflow.MVCTHymeleafcontroller;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(basePackages = {
        "com.example.marketflow.MVCTHymeleafcontroller",
        "com.example.marketflow.Seller.Controller"
})
public class NavigationModelAdvice {

    @ModelAttribute
    public void addAvailableWorkspaces(Authentication authentication, Model model) {
        model.addAttribute("isSeller", hasRole(authentication, "ROLE_SELLER"));
        model.addAttribute("isOwner", hasRole(authentication, "ROLE_OWNER"));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
