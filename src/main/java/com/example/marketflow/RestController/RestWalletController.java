package com.example.marketflow.RestController;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.marketflow.exception.AuthenticationRequiredException;
import com.example.marketflow.payment.TrounsferMoneyOnSellerCard;
import com.example.marketflow.marketplace.MarketplaceViews.PageView;
import com.example.marketflow.service.WalletService;
import com.example.marketflow.service.WalletService.WalletTransactionView;
import com.example.marketflow.service.WalletService.WalletView;
import com.example.marketflow.service.WalletService.WithdrawalView;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class RestWalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletView wallet(HttpSession session) {
        return walletService.sellerWallet(actor(session));
    }

    @GetMapping("/transactions")
    public PageView<WalletTransactionView> transactions(
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageView.of(walletService.sellerTransactions(actor(session), page, size));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WithdrawalView> RecapMonetFromMainWallet(
            @Valid @RequestBody TrounsferMoneyOnSellerCard request,
            HttpSession session
    ) {
        Long sellerId = actor(session);

        WithdrawalView withdrawal = walletService.transerMoneyfromWallerforSeller(
                sellerId,
                request.cardId(),
                request.amount(),
                request.idempotencyKey()
        );
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/wallet/withdrawals/" + withdrawal.transactionId()))
                .body(withdrawal);
    }

    @GetMapping("/withdrawals/{transactionId}")
    public WithdrawalView withdrawal(
            @org.springframework.web.bind.annotation.PathVariable Long transactionId,
            HttpSession session
    ) {
        return walletService.withdrawal(actor(session), transactionId);
    }

    private Long actor(HttpSession session) {
        Long sellerId = (Long) session.getAttribute("userId");
        if (sellerId == null) {
            throw new AuthenticationRequiredException();
        }
        return sellerId;
    }
}
