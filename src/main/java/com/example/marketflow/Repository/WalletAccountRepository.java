package com.example.marketflow.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment.WalletType;

import jakarta.persistence.LockModeType;

@Repository
public interface WalletAccountRepository
        extends JpaRepository<WalletAccountEntity, Long> {

    Optional<WalletAccountEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    Optional<WalletAccountEntity> findByType(WalletType type);

    /*
     * Метод понадобится при начислении денег продавцу.
     * PESSIMISTIC_WRITE блокирует строку до завершения транзакции.
    */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletAccountEntity> findLockedByUserId(Long userId);

    /*
     * Метод понадобится при начислении комиссии платформе.
    */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletAccountEntity> findLockedByType(WalletType type);
}
