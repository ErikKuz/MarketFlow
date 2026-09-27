package com.example.marketflow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.example.marketflow.infrastructure.Redis.FixedWindow.FixedWindowRateLimiter;
import com.example.marketflow.infrastructure.Redis.FixedWindow.RateLimitFilter;

class RateLimitFilterTest {

    @Test
    void usesRemoteAddressInsteadOfClientSuppliedKey() throws Exception {
        FixedWindowRateLimiter limiter = mock(FixedWindowRateLimiter.class);
        when(limiter.checkonratelimit("192.0.2.1")).thenReturn(false);
        RateLimitFilter filter = new RateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setRemoteAddr("192.0.2.1");
        request.addHeader("X-API-KEY", "changed-by-client");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertNull(chain.getRequest());
        verify(limiter).checkonratelimit("192.0.2.1");
    }

    @Test
    void doesNotLimitMvcPages() throws Exception {
        FixedWindowRateLimiter limiter = mock(FixedWindowRateLimiter.class);
        RateLimitFilter filter = new RateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/account/catalog");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
        verifyNoInteractions(limiter);
    }

    @Test
    void keepsApiAvailableWhenRedisIsUnavailable() throws Exception {
        FixedWindowRateLimiter limiter = mock(FixedWindowRateLimiter.class);
        when(limiter.checkonratelimit("192.0.2.1"))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        RateLimitFilter filter = new RateLimitFilter(limiter);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setRemoteAddr("192.0.2.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }
}
