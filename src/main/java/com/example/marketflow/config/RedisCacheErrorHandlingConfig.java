package com.example.marketflow.config;

import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableCaching
public class RedisCacheErrorHandlingConfig implements CachingConfigurer {


    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                warn("чтение", cache, key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                warn("запись", cache, key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                warn("очистка ключа", cache, key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                warn("полная очистка", cache, null, exception);
            }
        };
    }

    private void warn(String operation, Cache cache, Object key, RuntimeException exception) {
        log.warn(
                "Redis недоступен: операция кеша '{}' пропущена, cache={}, key={}",
                operation,
                cache.getName(),
                key,
                exception
        );
    }
}
