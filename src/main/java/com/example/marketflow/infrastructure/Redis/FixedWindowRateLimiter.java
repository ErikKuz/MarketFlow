package com.example.marketflow.infrastructure.Redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component 
@Slf4j 
@RequiredArgsConstructor
public class FixedWindowRateLimiter {
    private static final long maxcount = 10;
    private static final Duration windowDuration = Duration.ofMinutes(1);
    StringRedisTemplate stringRedistemplate;
    
    public boolean checkonratelimit(String clientId){
        
        long FixedWindow = System.currentTimeMillis()/windowDuration.toMillis();
        String key = "rate:"+clientId+"::"+FixedWindow;

        Long count = stringRedistemplate.opsForValue().increment(key);

        if(count == null){ 
            return false;
        } 
        if (count == 1)stringRedistemplate.expire(key,windowDuration);     
        if (count >maxcount){
            log.info("Пользователь с id:{} сделал слишком много запросов за {} минуту.Количество Запросов:{}",clientId,windowDuration,count);
            return false;
        }
        return true;
    }
}
