package com.example.marketflow.MVCTHymeleafcontroller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ExtendedModelMap;

class NavigationModelAdviceTest {

    private final NavigationModelAdvice advice = new NavigationModelAdvice();

    @Test
    void exposesSellerWorkspaceLinkForSeller() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "seller@example.com",
                "password",
                List.of(
                        new SimpleGrantedAuthority("ROLE_BUYER"),
                        new SimpleGrantedAuthority("ROLE_SELLER")
                )
        );
        var model = new ExtendedModelMap();

        advice.addAvailableWorkspaces(authentication, model);

        assertEquals(true, model.get("isSeller"));
        assertEquals(false, model.get("isOwner"));
    }

    @Test
    void hidesPrivilegedWorkspaceLinksForAnonymousUser() {
        var model = new ExtendedModelMap();

        advice.addAvailableWorkspaces(null, model);

        assertEquals(false, model.get("isSeller"));
        assertEquals(false, model.get("isOwner"));
    }
}
