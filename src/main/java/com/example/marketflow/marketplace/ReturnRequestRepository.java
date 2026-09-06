package com.example.marketflow.marketplace;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequestEntity, Long> {
    @Query("select r.orderId from ReturnRequestEntity r where r.id = :id")
    Optional<Long> findOrderId(@Param("id") Long id);
    Optional<ReturnRequestEntity> findByOrderId(Long orderId);
    boolean existsByOrderIdAndStatus(Long orderId, ReturnRequestEntity.Status status);
    Page<ReturnRequestEntity> findAllByOrderByRequestedAtDescIdDesc(Pageable pageable);
}
