package com.example.marketflow.Seller.Controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.WalletType;
import com.example.marketflow.service.PaymentCardService;
import com.example.marketflow.service.WalletService;
import com.example.marketflow.service.WalletService.WalletView;
import com.example.marketflow.service.WalletService.WithdrawalView;

@WebMvcTest(SellerWalletController.class)
@AutoConfigureMockMvc(addFilters = false)
class SellerWalletControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean WalletService walletService;
    @MockitoBean PaymentCardService paymentCardService;

    @Test
    void showsSellerWalletPage() throws Exception {
        when(walletService.sellerWallet(7L)).thenReturn(new WalletView(
                22L, WalletType.SELLER, new BigDecimal("15.00"),
                new BigDecimal("80.00"), BigDecimal.ZERO,
                Instant.parse("2026-09-15T10:00:00Z")
        ));
        when(walletService.sellerTransactions(7L, 0, 20)).thenReturn(Page.empty());
        when(paymentCardService.getUserPaymentCards(7L)).thenReturn(List.of());

        mockMvc.perform(get("/seller/wallet").session(session()))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/seller-wallet"))
                .andExpect(model().attributeExists("wallet", "transactions", "withdrawRequest"));
    }

    @Test
    void acceptsWithdrawalAndRedirectsWithTrackableTransactionId() throws Exception {
        when(walletService.transerMoneyfromWallerforSeller(
                7L, 15L, new BigDecimal("60.00"), "withdrawal-key"
        )).thenReturn(new WithdrawalView(
                81L, 15L, new BigDecimal("60.00"), TransactionStatus.PENDING,
                "withdrawal-key", Instant.parse("2026-09-15T10:00:00Z")
        ));

        mockMvc.perform(post("/seller/wallet/withdraw")
                        .session(session())
                        .param("cardId", "15")
                        .param("amount", "60.00")
                        .param("idempotencyKey", "withdrawal-key"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/seller/wallet?withdrawalAccepted=true&withdrawalId=81"
                ));

        verify(walletService).transerMoneyfromWallerforSeller(
                7L, 15L, new BigDecimal("60.00"), "withdrawal-key"
        );
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        return session;
    }
}
