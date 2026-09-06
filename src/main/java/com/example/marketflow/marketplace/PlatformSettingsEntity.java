package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "platform_settings")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformSettingsEntity {
    @Id private Long id;
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4) private BigDecimal commissionRate;
    public PlatformSettingsEntity(BigDecimal rate) { id = 1L; commissionRate = rate; }
    public void changeCommission(BigDecimal rate) { commissionRate = rate; }
}
