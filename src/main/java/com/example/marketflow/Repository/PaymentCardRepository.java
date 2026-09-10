package com.example.marketflow.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.marketflow.payment_cards.PaymentCardEntity;

@Repository
public interface PaymentCardRepository extends JpaRepository<PaymentCardEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCardEntity c where c.id = :cardId and c.userid = :buyerId and c.active = true")
    Optional<PaymentCardEntity> findForPayment(@Param("cardId") Long cardId, @Param("buyerId") Long buyerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCardEntity c where c.id = :cardId and c.userid = :userId")
    Optional<PaymentCardEntity> findForBalanceUpdate(
            @Param("cardId") Long cardId,
            @Param("userId") Long userId
    );

    List<PaymentCardEntity> findAllByUserid(Long userid);

    List<PaymentCardEntity> findAllByUseridAndActiveTrue(Long userid);


}
