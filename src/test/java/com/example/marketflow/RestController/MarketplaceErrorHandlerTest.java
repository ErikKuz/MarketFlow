package com.example.marketflow.RestController;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import com.example.marketflow.exception.GlobalRestExceptionHandler;
import com.example.marketflow.marketplace.MarketplaceException;

class MarketplaceErrorHandlerTest {

    @Test
    void errorBodyKeepsMarketplaceStatusAndCode() {
        var request = new MockHttpServletRequest("POST", "/api/v1/seller/orders/1/process");
        var exception = MarketplaceException.conflict("Order cannot be processed");

        var response = new GlobalRestExceptionHandler().handleMarketplace(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().status());
        assertEquals("MARKETPLACE_CONFLICT", response.getBody().code());
    }
}
