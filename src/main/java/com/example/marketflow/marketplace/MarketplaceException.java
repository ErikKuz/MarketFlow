package com.example.marketflow.marketplace;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class MarketplaceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public MarketplaceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public static MarketplaceException conflict(String message) {
        return new MarketplaceException(HttpStatus.CONFLICT, "MARKETPLACE_CONFLICT", message);
    }
    public static MarketplaceException missing(String message) {
        return new MarketplaceException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
