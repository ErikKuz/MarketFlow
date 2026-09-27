package com.example.marketflow.infrastructure.Redis.FixedWindow;

import java.io.IOException;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component 
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true")
@Slf4j 
@RequiredArgsConstructor 
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {
    private final FixedWindowRateLimiter fixedWindowRateLimiter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String client = Optional.ofNullable(req.getRemoteAddr()).orElse("unknown");
        boolean allowed;
        try {
            allowed = fixedWindowRateLimiter.checkonratelimit(client);
        } catch (DataAccessException exception) {
            log.warn("Redis недоступен, проверка лимита запросов пропущена: {}", exception.toString());
            filterChain.doFilter(req, response);
            return;
        }

        if (!allowed) {
            response.setStatus(429);
            response.getWriter().write("Rate limit exceeded");
            return;
        }
        filterChain.doFilter(req, response);
    }
}
