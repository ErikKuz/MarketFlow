package com.example.marketflow.marketplace;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("marketflow")
public class MarketplaceProperties {
    @Min(1) private int paymentTimeoutMinutes = 30;
    @Min(1) private int returnWindowDays = 7;
}
