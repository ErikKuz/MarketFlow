package com.example.marketflow.marketplace;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MarketplaceConfiguration {
    @Bean
    public Clock marketplaceClock() {
        return Clock.systemUTC();
    }
}
