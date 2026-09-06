package com.example.marketflow.marketplace;

import java.time.Clock;
import org.springframework.context.annotation.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.beans.factory.annotation.Value;

@Configuration @EnableScheduling @EnableConfigurationProperties(MarketplaceProperties.class)
public class MarketplaceConfiguration {
    @Bean public Clock marketplaceClock() { return Clock.systemUTC(); }

    @Bean
    public ApplicationRunner initializePlatform(AdministrationService administration,
            @Value("${marketflow.owner.email:}") String email,
            @Value("${marketflow.owner.password:}") String password) {
        return args -> administration.initializePlatform(email, password);
    }
}
