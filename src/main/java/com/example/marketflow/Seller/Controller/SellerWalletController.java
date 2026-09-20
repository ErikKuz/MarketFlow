package com.example.marketflow.Seller.Controller;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.marketflow.exception.AuthenticationRequiredException;
import com.example.marketflow.marketplace.MarketplaceViews.PageView;
import com.example.marketflow.payment.TrounsferMoneyOnSellerCard;
import com.example.marketflow.service.PaymentCardService;
import com.example.marketflow.service.WalletService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/seller/wallet")
@RequiredArgsConstructor
public class SellerWalletController {
    private static final int TRANSACTIONS_PAGE_SIZE = 20;

    private final WalletService walletService;
    private final PaymentCardService paymentCardService;

    @GetMapping
    public String wallet(
            HttpSession session,
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "false") boolean withdrawalAccepted,
            @RequestParam(required = false) Long withdrawalId
    ) {
        Long sellerId = actor(session);
        populatePage(model, sellerId, page);
        model.addAttribute(
                "withdrawRequest",
                new TrounsferMoneyOnSellerCard(null, null, UUID.randomUUID().toString())
        );
        model.addAttribute("withdrawalAccepted", withdrawalAccepted);
        model.addAttribute("withdrawalId", withdrawalId);
        return "workspace/seller-wallet";
    }

    @PostMapping("/withdraw")
    public String withdraw(
            @Valid @ModelAttribute("withdrawRequest") TrounsferMoneyOnSellerCard request,
            BindingResult bindingResult,
            HttpSession session,
            Model model,
            @RequestParam(defaultValue = "0") int page
    ) {
        Long sellerId = actor(session);
        if (bindingResult.hasErrors()) {
            populatePage(model, sellerId, page);
            model.addAttribute("withdrawalAccepted", false);
            return "workspace/seller-wallet";
        }

        var withdrawal = walletService.transerMoneyfromWallerforSeller(
                sellerId,
                request.cardId(),
                request.amount(),
                request.idempotencyKey()
        );
        return "redirect:/seller/wallet?withdrawalAccepted=true&withdrawalId="
                + withdrawal.transactionId();
    }

    private void populatePage(Model model, Long sellerId, int page) {
        var cards = paymentCardService.getUserPaymentCards(sellerId);
        model.addAttribute("wallet", walletService.sellerWallet(sellerId));
        model.addAttribute(
                "transactions",
                PageView.of(walletService.sellerTransactions(sellerId, page, TRANSACTIONS_PAGE_SIZE))
        );
        model.addAttribute("cards", cards);
        model.addAttribute("hasActiveCards", cards.stream().anyMatch(card -> card.active()));
    }

    private Long actor(HttpSession session) {
        Long sellerId = (Long) session.getAttribute("userId");
        if (sellerId == null) {
            throw new AuthenticationRequiredException();
        }
        return sellerId;
    }
}
