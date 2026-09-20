package com.example.marketflow.MVCTHymeleafcontroller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.payment_cards.AddPaymentCardRequest;
import com.example.marketflow.service.PaymentCardService;

@WebMvcTest(PaymentCardController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentCardControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean PaymentCardService paymentCardService;

    @Test
    void showsCardListWithSuccessMessageAfterRedirect() throws Exception {
        when(paymentCardService.getUserPaymentCards(7L)).thenReturn(List.of());

        mockMvc.perform(get("/account/account/cards")
                        .session(session())
                        .param("added", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("showCards"))
                .andExpect(model().attribute("cardAdded", true));
    }

    @Test
    void addsCardUsingPostRedirectGet() throws Exception {
        mockMvc.perform(post("/account/account/cards")
                        .session(session())
                        .param("cardtoken", "card_test")
                        .param("maskedNumber", "**** 1234")
                        .param("balance", "100.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/account/cards?added=true"));

        verify(paymentCardService).addPaymentCard(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.any(AddPaymentCardRequest.class)
        );
    }

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        return session;
    }
}
