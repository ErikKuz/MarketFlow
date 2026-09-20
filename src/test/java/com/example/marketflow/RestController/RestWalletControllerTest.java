package com.example.marketflow.RestController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.exception.GlobalRestExceptionHandler;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletType;
import com.example.marketflow.service.WalletService;
import com.example.marketflow.service.WalletService.WalletTransactionView;
import com.example.marketflow.service.WalletService.WalletView;
import com.example.marketflow.service.WalletService.WithdrawalView;

@WebMvcTest(RestWalletController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalRestExceptionHandler.class)
class RestWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    void returnsCurrentSellerWallet() throws Exception {
        when(walletService.sellerWallet(7L)).thenReturn(new WalletView(
                22L,
                WalletType.SELLER,
                new BigDecimal("15.00"),
                new BigDecimal("80.00"),
                BigDecimal.ZERO,
                Instant.parse("2026-09-14T10:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/wallet").session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(22))
                .andExpect(jsonPath("$.type").value("SELLER"))
                .andExpect(jsonPath("$.pendingBalance").value(15.00))
                .andExpect(jsonPath("$.availableBalance").value(80.00))
                .andExpect(jsonPath("$.reservedBalance").value(0));

        verify(walletService).sellerWallet(7L);
    }

    @Test
    void returnsCurrentSellerWalletTransactions() throws Exception {
        WalletTransactionView transaction = new WalletTransactionView(
                41L,
                101L,
                31L,
                TransactionType.SELLER_PENDINGWALLET,
                new BigDecimal("90.00"),
                TransactionStatus.COMPLETED,
                Instant.parse("2026-09-14T10:05:00Z")
        );
        when(walletService.sellerTransactions(7L, 1, 10)).thenReturn(
                new PageImpl<>(List.of(transaction), PageRequest.of(1, 10), 11)
        );

        mockMvc.perform(get("/api/v1/wallet/transactions")
                        .session(session(7L))
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.content[0].id").value(41))
                .andExpect(jsonPath("$.content[0].type").value("SELLER_PENDINGWALLET"))
                .andExpect(jsonPath("$.content[0].amount").value(90.00));

        verify(walletService).sellerTransactions(7L, 1, 10);
    }

    @Test
    void withdrawPassesSellerAndRequestToService() throws Exception {
        when(walletService.transerMoneyfromWallerforSeller(
                7L, 15L, new BigDecimal("60.00"), "withdrawal-key"
        )).thenReturn(new WithdrawalView(
                81L,
                15L,
                new BigDecimal("60.00"),
                TransactionStatus.PENDING,
                "withdrawal-key",
                Instant.parse("2026-09-15T10:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/wallet/withdraw")
                        .session(session(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": 15,
                                  "amount": 60.00,
                                  "idempotencyKey": "withdrawal-key"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", "/api/v1/wallet/withdrawals/81"))
                .andExpect(jsonPath("$.transactionId").value(81))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(walletService).transerMoneyfromWallerforSeller(
                7L,
                15L,
                new BigDecimal("60.00"),
                "withdrawal-key"
        );
    }

    @Test
    void returnsWithdrawalStatusForCurrentSeller() throws Exception {
        when(walletService.withdrawal(7L, 81L)).thenReturn(new WithdrawalView(
                81L,
                15L,
                new BigDecimal("60.00"),
                TransactionStatus.COMPLETED,
                "withdrawal-key",
                Instant.parse("2026-09-15T10:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/wallet/withdrawals/81").session(session(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(81))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(walletService).withdrawal(7L, 81L);
    }

    @Test
    void withdrawRejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/withdraw")
                        .session(session(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": 15,
                                  "amount": 0,
                                  "idempotencyKey": "withdrawal-key"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        verifyNoInteractions(walletService);
    }

    @Test
    void withdrawRequiresAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": 15,
                                  "amount": 60.00,
                                  "idempotencyKey": "withdrawal-key"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(walletService);
    }

    private MockHttpSession session(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);
        return session;
    }
}
