package com.example.marketflow.Repository;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import com.example.marketflow.payment_cards.PaymentCardEntity;

@DataJpaTest
class PaymentCardRepositoryTest {
    @Autowired PaymentCardRepository cards;

    @Test
    void paymentLockReturnsOnlyAnActiveCardOwnedByTheBuyer() {
        var active = cards.saveAndFlush(card(7L, true));
        var inactive = cards.saveAndFlush(card(7L, false));
        assertEquals(active.getId(), cards.findForPayment(active.getId(), 7L).orElseThrow().getId());
        assertTrue(cards.findForPayment(active.getId(), 8L).isEmpty());
        assertTrue(cards.findForPayment(inactive.getId(), 7L).isEmpty());
    }

    private PaymentCardEntity card(Long userId, boolean active) {
        var card = new PaymentCardEntity(userId, java.util.UUID.randomUUID().toString(),
                "**** **** **** 4242", new BigDecimal("100.00"));
        card.setActive(active);
        return card;
    }
}
