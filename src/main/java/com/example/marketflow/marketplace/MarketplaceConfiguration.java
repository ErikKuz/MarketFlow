package com.example.marketflow.marketplace;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketplaceConfiguration {//Нужно чтобы получать текущее время
    @Bean
    public Clock marketplaceClock() {
        return Clock.systemUTC();
    }
}
