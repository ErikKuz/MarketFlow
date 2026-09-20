package com.example.marketflow.RestController;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.marketflow.Order.OrderDetailsDto;
import com.example.marketflow.Order.OrderItemDto;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.exception.GlobalRestExceptionHandler;
import com.example.marketflow.exception.OrderNotFoundException;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.service.OrderService;
import com.example.marketflow.service.OrderService.OrderHistoryView;

@WebMvcTest(RestOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalRestExceptionHandler.class)
class RestOrderControllerTest {

    private static final Long BUYER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void createOrderReturns201LocationAndOrderId() throws Exception {
        when(orderService.createOrder(BUYER_ID)).thenReturn(42L);

        mockMvc.perform(post("/api/v1/orders").session(authenticatedSession()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/orders/42"))
                .andExpect(jsonPath("$.orderId").value(42));

        verify(orderService).createOrder(BUYER_ID);
    }

    @Test
    void createOrderReturns401WithoutAuthenticatedSession() throws Exception {
        mockMvc.perform(post("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(orderService);
    }

    @Test
    void getOrderReturnsOrderDetails() throws Exception {
        OrderItemDto item = new OrderItemDto(
                10L,
                3L,
                "Mechanical Keyboard",
                new BigDecimal("7490.00"),
                2,
                new BigDecimal("14980.00"),
                "https://example.com/keyboard.jpg"
        );
        OrderDetailsDto order = new OrderDetailsDto(
                42L,
                OrderStatus.CREATED,
                PaymentStatus.NOT_PAID,
                new BigDecimal("14980.00"),
                Instant.parse("2026-09-03T10:00:00Z"),
                List.of(item)
        );

        when(orderService.getOrderDetails(42L, BUYER_ID)).thenReturn(order);

        mockMvc.perform(get("/api/v1/orders/{orderId}", 42L)
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.paymentStatus").value("NOT_PAID"))
                .andExpect(jsonPath("$.totalPrice").value(14980.00))
                .andExpect(jsonPath("$.items[0].productId").value(10))
                .andExpect(jsonPath("$.items[0].quantity").value(2));

        verify(orderService).getOrderDetails(42L, BUYER_ID);
    }

    @Test
    void getOrderReturns400ForInvalidOrderId() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}", 0)
                        .session(authenticatedSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_ID"));

        verifyNoInteractions(orderService);
    }

    @Test
    void getOrderReturns404WhenOrderDoesNotExist() throws Exception {
        when(orderService.getOrderDetails(42L, BUYER_ID))
                .thenThrow(new OrderNotFoundException(42L));

        mockMvc.perform(get("/api/v1/orders/{orderId}", 42L)
                        .session(authenticatedSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void getOrderHistoryReturnsBuyerTimeline() throws Exception {
        UUID eventId = UUID.fromString("8b679b54-37f9-4d9a-8f5f-50e3559ed38f");
        when(orderService.getOrderHistory(42L, BUYER_ID)).thenReturn(List.of(
                new OrderHistoryView(
                        eventId,
                        MarketFlowEventType.ORDER_PAID,
                        "Заказ оплачен",
                        null,
                        "CREATED",
                        "CONFIRMED",
                        Instant.parse("2026-09-14T10:05:00Z")
                )
        ));

        mockMvc.perform(get("/api/v1/orders/{orderId}/history", 42L)
                        .session(authenticatedSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$[0].eventType").value("ORDER_PAID"))
                .andExpect(jsonPath("$[0].description").value("Заказ оплачен"))
                .andExpect(jsonPath("$[0].previousStatus").value("CREATED"))
                .andExpect(jsonPath("$[0].currentStatus").value("CONFIRMED"));

        verify(orderService).getOrderHistory(42L, BUYER_ID);
    }

    @Test
    void getOrderHistoryRejectsInvalidOrderId() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{orderId}/history", 0L)
                        .session(authenticatedSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_ID"));

        verifyNoInteractions(orderService);
    }

    @Test
    void cancelOrderReturns204AndCallsServiceForAuthenticatedBuyer()
            throws Exception {
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", 42L)
                        .session(authenticatedSession()))
                .andExpect(status().isNoContent());

        verify(orderService).cancelOrder(42L, BUYER_ID);
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", BUYER_ID);
        return session;
    }
}
