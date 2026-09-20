package com.example.marketflow.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;

class RedisCacheConfigTest {

    @Test
    void cacheErrorsDoNotBreakBusinessRequests() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("catalogProducts");
        RuntimeException redisUnavailable = new RuntimeException("Redis unavailable");
        var handler = new RedisCacheConfig().errorHandler();

        assertDoesNotThrow(() -> handler.handleCacheGetError(redisUnavailable, cache, "all"));
        assertDoesNotThrow(() -> handler.handleCachePutError(redisUnavailable, cache, "all", "value"));
        assertDoesNotThrow(() -> handler.handleCacheEvictError(redisUnavailable, cache, "all"));
        assertDoesNotThrow(() -> handler.handleCacheClearError(redisUnavailable, cache));
    }
}
