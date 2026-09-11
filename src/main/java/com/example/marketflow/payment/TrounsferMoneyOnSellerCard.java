package com.example.marketflow.payment;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TrounsferMoneyOnSellerCard(
        @NotNull @Positive Long cardId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Size(max = 150) String idempotencyKey
) {
}
