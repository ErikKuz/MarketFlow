package com.example.marketflow.RestController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@WebMvcTest(RestPlatformWalletController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalRestExceptionHandler.class)
class RestPlatformWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    void returnsPlatformWalletForCurrentOwner() throws Exception {
        when(walletService.platformWallet(99L)).thenReturn(new WalletView(
                90L,
                WalletType.PLATFORM,
                new BigDecimal("30.00"),
                new BigDecimal("120.00"),
                BigDecimal.ZERO,
                Instant.parse("2026-09-14T11:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/platform/wallet").session(session(99L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(90))
                .andExpect(jsonPath("$.type").value("PLATFORM"))
                .andExpect(jsonPath("$.pendingBalance").value(30.00))
                .andExpect(jsonPath("$.availableBalance").value(120.00));

        verify(walletService).platformWallet(99L);
    }

    @Test
    void returnsPlatformCommissionTransactions() throws Exception {
        WalletTransactionView transaction = new WalletTransactionView(
                51L,
                101L,
                31L,
                TransactionType.PLATFORM_COMMISSION,
                new BigDecimal("10.00"),
                TransactionStatus.COMPLETED,
                Instant.parse("2026-09-14T11:05:00Z")
        );
        when(walletService.platformTransactions(99L, 0, 20)).thenReturn(
                new PageImpl<>(List.of(transaction), PageRequest.of(0, 20), 1)
        );

        mockMvc.perform(get("/api/v1/platform/wallet/transactions").session(session(99L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("PLATFORM_COMMISSION"))
                .andExpect(jsonPath("$.content[0].amount").value(10.00))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));

        verify(walletService).platformTransactions(99L, 0, 20);
    }

    @Test
    void requiresAuthenticatedSession() throws Exception {
        mockMvc.perform(get("/api/v1/platform/wallet"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(walletService);
    }

    private MockHttpSession session(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", userId);
        return session;
    }
}
