package com.example.marketflow.Repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment.WalletType;

@DataJpaTest
class WalletAccountRepositoryTest {

    @Autowired
    private WalletAccountRepository walletAccountRepository;

    @Test
    void shouldSaveAndFindSellerWallet() {
        WalletAccountEntity saved = walletAccountRepository.saveAndFlush(
                WalletAccountEntity.seller(42L)
        );

        WalletAccountEntity found = walletAccountRepository
                .findByUserId(42L)
                .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals(WalletType.SELLER, found.getType());
        assertEquals(0, found.getPendingBalance().compareTo(BigDecimal.ZERO));
        assertEquals(0, found.getAvailableBalance().compareTo(BigDecimal.ZERO));
        assertTrue(walletAccountRepository.findLockedByUserId(42L).isPresent());
    }

    @Test
    void shouldSaveAndFindPlatformWallet() {
        walletAccountRepository.saveAndFlush(WalletAccountEntity.platform());

        WalletAccountEntity found = walletAccountRepository
                .findByType(WalletType.PLATFORM)
                .orElseThrow();

        assertNull(found.getUserId());
        assertEquals(WalletType.PLATFORM, found.getType());
        assertTrue(walletAccountRepository.findLockedByType(WalletType.PLATFORM).isPresent());
    }
}
