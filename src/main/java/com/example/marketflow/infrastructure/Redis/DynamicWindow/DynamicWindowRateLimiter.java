package com.example.marketflow.infrastructure.Redis.DynamicWindow;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DynamicWindowRateLimiter {
    private static final long MAX_COUNT = 10;
    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;

    public boolean checkonratelimit(String clientId) {
        long now = System.currentTimeMillis();
        String key = "rate:dynamic:" + clientId;
        var requests = stringRedisTemplate.opsForZSet();

        requests.removeRangeByScore(key, 0, now - WINDOW_DURATION.toMillis()); // Удаляем старые запросы из множества клиента.
        Long count = requests.zCard(key);//сколько запросов уже
        if (count == null || count >= MAX_COUNT) {
            return false;
        }

        String requestId = now + ":" + UUID.randomUUID();
        Boolean added = requests.add(key, requestId, now);
        if (!Boolean.TRUE.equals(added)) {
            return false;
        }
        stringRedisTemplate.expire(key, WINDOW_DURATION);
        return true;
    }
}
