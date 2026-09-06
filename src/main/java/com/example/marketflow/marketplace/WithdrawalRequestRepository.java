package com.example.marketflow.marketplace;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequestEntity, Long> {
    Optional<WithdrawalRequestEntity> findBySellerIdAndRequestKey(Long sellerId, String requestKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalRequestEntity w where w.id = :id")
    Optional<WithdrawalRequestEntity> lockById(@Param("id") Long id);
    Page<WithdrawalRequestEntity> findAllBySellerIdOrderByRequestedAtDescIdDesc(Long sellerId, Pageable pageable);
    Page<WithdrawalRequestEntity> findAllByOrderByRequestedAtDescIdDesc(Pageable pageable);
}
