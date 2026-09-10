package com.example.marketflow.security;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.marketflow.Repository.UserRepository;
import com.example.marketflow.Repository.userRoleRepository;
import com.example.marketflow.User.UserStatus;
import lombok.RequiredArgsConstructor;

/** Обновляет сохранённые статус и роли перед авторизацией, в том числе для уже открытых сессий. */
@RequiredArgsConstructor
public class SessionAccountFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final userRoleRepository roles;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof MarketFlowPrincipal principal) {
            var user = users.findById(principal.getUserId()).orElse(null);
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
            } else {
                var authorities = roles.findRoleNamesByUserId(user.getId()).stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                var refreshed = new MarketFlowPrincipal(user, authorities);
                var updated = UsernamePasswordAuthenticationToken.authenticated(refreshed, null, authorities);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(updated);
                SecurityContextHolder.setContext(context);
                var session = request.getSession(false);
                if (session != null) {
                    session.setAttribute("userId", user.getId());
                    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
