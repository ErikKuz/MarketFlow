package com.example.marketflow.marketplace;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SellerApplicationRepository extends JpaRepository<SellerApplicationEntity, Long> {
    Optional<SellerApplicationEntity> findByUserId(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SellerApplicationEntity a where a.id = :id")
    Optional<SellerApplicationEntity> lockById(@Param("id") Long id);
    Page<SellerApplicationEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
