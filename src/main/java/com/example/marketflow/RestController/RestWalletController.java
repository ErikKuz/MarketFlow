package com.example.marketflow.RestController;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.marketflow.exception.AuthenticationRequiredException;
import com.example.marketflow.payment.TrounsferMoneyOnSellerCard;
import com.example.marketflow.service.WalletService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class RestWalletController {

    private final WalletService walletService;

    @PostMapping("/withdraw")
    public ResponseEntity<Void> RecapMonetFromMainWallet(
            @Valid @RequestBody TrounsferMoneyOnSellerCard request,
            HttpSession session
    ) {
        Long sellerId = (Long) session.getAttribute("userId");
        if (sellerId == null) {
            throw new AuthenticationRequiredException();
        }

        walletService.transerMoneyfromWallerforSeller(
                sellerId,
                request.cardId(),
                request.amount(),
                request.idempotencyKey()
        );
        return ResponseEntity.noContent().build();
    }
}
