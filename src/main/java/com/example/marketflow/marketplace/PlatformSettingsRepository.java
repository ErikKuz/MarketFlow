package com.example.marketflow.marketplace;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettingsEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlatformSettingsEntity s where s.id = 1")
    Optional<PlatformSettingsEntity> lockSettings();
}
