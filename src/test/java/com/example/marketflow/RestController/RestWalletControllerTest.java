package com.example.marketflow.RestController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.exception.GlobalRestExceptionHandler;
import com.example.marketflow.service.WalletService;

@WebMvcTest(RestWalletController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalRestExceptionHandler.class)
class RestWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Test
    void withdrawPassesSellerAndRequestToService() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        mockMvc.perform(post("/api/v1/wallet/withdraw")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": 15,
                                  "amount": 60.00,
                                  "idempotencyKey": "withdrawal-key"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(walletService).transerMoneyfromWallerforSeller(
                7L,
                15L,
                new BigDecimal("60.00"),
                "withdrawal-key"
        );
    }

    @Test
    void withdrawRejectsInvalidRequest() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);

        mockMvc.perform(post("/api/v1/wallet/withdraw")
                        .session(session)
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
}
