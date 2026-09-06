package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

public final class MarketplaceRequests {
    private MarketplaceRequests() {}
    public record Reason(@NotBlank @Size(max = 1000) String reason) {}
    public record ReturnDecision(boolean approved, boolean restock, @NotBlank @Size(max = 1000) String reason) {}
    public record Decision(boolean approved, @NotBlank @Size(max = 1000) String reason) {}
    public record Withdrawal(@NotNull @Positive Long cardId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 100) String idempotencyKey) {}
    public record Commission(@NotNull @DecimalMin("0.0") @DecimalMax("0.99")
            @Digits(integer = 1, fraction = 4) BigDecimal rate) {}
    public record Staff(@NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 12, max = 100) String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Pattern(regexp = "SELLER_MODERATOR|ANALYST") String role) {}
    public record RoleChange(@NotBlank @Pattern(regexp = "SELLER_MODERATOR|ANALYST") String role, boolean granted) {}
    public record Block(boolean blocked, @NotBlank @Size(max = 1000) String reason) {}
    public record Visibility(boolean hidden, @NotBlank @Size(max = 1000) String reason) {}
}
