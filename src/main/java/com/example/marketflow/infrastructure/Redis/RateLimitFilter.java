package com.example.marketflow.infrastructure.Redis;

import java.io.IOException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component 
@Slf4j 
@RequiredArgsConstructor 
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {
    private final FixedWindowRateLimiter fixedWindowRateLimiter;

    @Value("${rate-limit.enabled}")
    private boolean enableRatelimiting;

    @Override
    protected void doFilterInternal(
            HttpServletRequest req,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!enableRatelimiting) {
            filterChain.doFilter(req, response);
            return;
        }

        String client = Optional.ofNullable(req.getHeader("X-API-KEY"))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> Optional.ofNullable(req.getRemoteAddr()).orElse("unknown"));

        boolean allowed = fixedWindowRateLimiter.checkonratelimit(client);

        if (!allowed) {
            response.setStatus(429);
            response.getWriter().write("Rate limit exceeded");
            return;
        }
        filterChain.doFilter(req, response);
    }
}
