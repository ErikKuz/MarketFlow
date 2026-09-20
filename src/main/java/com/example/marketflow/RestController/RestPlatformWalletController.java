package com.example.marketflow.RestController;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.marketflow.exception.AuthenticationRequiredException;
import com.example.marketflow.marketplace.MarketplaceViews.PageView;
import com.example.marketflow.service.WalletService;
import com.example.marketflow.service.WalletService.WalletTransactionView;
import com.example.marketflow.service.WalletService.WalletView;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/platform/wallet")
@RequiredArgsConstructor
public class RestPlatformWalletController {
    private final WalletService walletService;

    @GetMapping
    public WalletView wallet(HttpSession session) {
        return walletService.platformWallet(actor(session));
    }

    @GetMapping("/transactions")
    public PageView<WalletTransactionView> transactions(
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return PageView.of(walletService.platformTransactions(actor(session), page, size));
    }

    private Long actor(HttpSession session) {
        Long ownerId = (Long) session.getAttribute("userId");
        if (ownerId == null) {
            throw new AuthenticationRequiredException();
        }
        return ownerId;
    }
}
