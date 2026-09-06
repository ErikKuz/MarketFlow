package com.example.marketflow.marketplace;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
    Page<AuditEventEntity> findAllByActorIdOrderByCreatedAtDescIdDesc(Long actorId, Pageable pageable);
    List<AuditEventEntity> findAllByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
    Page<AuditEventEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
