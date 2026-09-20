package com.example.marketflow.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.Seller.Service.SellerDashboardService;
import com.example.marketflow.payment.WalletType;
import com.example.marketflow.service.CartService;
import com.example.marketflow.service.WalletService;
import com.example.marketflow.service.WalletService.WalletView;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerDashboardService sellerDashboardService;

    @MockitoBean
    private CartService cartService;

    @MockitoBean
    private WalletService walletService;

    @Test
    void shouldAllowPublicProductCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnJson401ForProtectedApi() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/api/v1/account"));
    }

    @Test
    void shouldReturnJson403WhenBuyerCallsSellerApi() throws Exception {
        mockMvc.perform(get("/api/v1/seller/products")
                        .with(user("buyer@example.com").roles("BUYER"))
                        .sessionAttr("userId", 7L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowSellerToCallSellerApi() throws Exception {
        when(sellerDashboardService.showallproductBySellerID(7L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/seller/products")
                        .with(user("seller@example.com").roles("SELLER"))
                        .sessionAttr("userId", 7L))
                .andExpect(status().isOk());

        verify(sellerDashboardService).showallproductBySellerID(7L);
    }

    @Test
    void shouldAllowOnlySellerToReadSellerWallet() throws Exception {
        when(walletService.sellerWallet(7L)).thenReturn(new WalletView(
                22L,
                WalletType.SELLER,
                BigDecimal.ZERO,
                new BigDecimal("80.00"),
                BigDecimal.ZERO,
                Instant.parse("2026-09-14T10:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/wallet")
                        .with(user("seller@example.com").roles("SELLER"))
                        .sessionAttr("userId", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SELLER"));

        mockMvc.perform(get("/api/v1/wallet")
                        .with(user("buyer@example.com").roles("BUYER"))
                        .sessionAttr("userId", 8L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(walletService).sellerWallet(7L);
    }

    @Test
    void shouldAllowOnlyOwnerToReadPlatformWallet() throws Exception {
        when(walletService.platformWallet(99L)).thenReturn(new WalletView(
                90L,
                WalletType.PLATFORM,
                new BigDecimal("30.00"),
                new BigDecimal("120.00"),
                BigDecimal.ZERO,
                Instant.parse("2026-09-14T11:00:00Z")
        ));

        mockMvc.perform(get("/api/v1/platform/wallet")
                        .with(user("owner@example.com").roles("OWNER"))
                        .sessionAttr("userId", 99L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PLATFORM"));

        mockMvc.perform(get("/api/v1/platform/wallet")
                        .with(user("seller@example.com").roles("SELLER"))
                        .sessionAttr("userId", 7L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(walletService).platformWallet(99L);
    }

    @Test
    void shouldRejectStateChangingRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user("buyer@example.com").roles("BUYER"))
                        .sessionAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowStateChangingRequestWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .with(user("buyer@example.com").roles("BUYER"))
                        .with(csrf().asHeader())
                        .sessionAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10}"))
                .andExpect(status().isNoContent());

        verify(cartService).addProductToCart(7L, 10L);
    }

    @Test
    void shouldExposeCsrfTokenToAnonymousClient() throws Exception {
        mockMvc.perform(get("/api/v1/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void mvcLogoutIsHandledBySpringSecurityAndRedirectsHome() throws Exception {
        mockMvc.perform(post("/logout")
                        .with(user("seller@example.com").roles("BUYER", "SELLER"))
                        .with(csrf())
                        .sessionAttr("userId", 7L))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
